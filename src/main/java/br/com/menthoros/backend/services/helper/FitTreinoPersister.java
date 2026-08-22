package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.fit.FitImportResultado;
import br.com.menthoros.backend.dto.fit.FitLapData;
import br.com.menthoros.backend.dto.fit.FitSessionData;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.EtapaRealizada;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.enums.TreinoExecucaoStatus;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.mapper.TreinoMapper;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.services.IngestaoTreinoRealizadoService;
import br.com.menthoros.backend.util.Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.UUID;

/**
 * Persiste o {@link TreinoRealizado} resultante de um import de .fit já parseado — isolado em
 * seu próprio componente transacional para que o parse do binário (CPU/IO-bound, sem acesso a
 * banco) em {@code FitUploadServiceImpl} possa acontecer FORA da transação, sem segurar uma
 * conexão de banco durante a decodificação.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FitTreinoPersister {

    private static final String CRIADO_POR_GARMIN = "GARMIN";

    // Faixas de sanidade para dados vindos do .fit (upload não confiável): fora delas → null,
    // nunca persistir lixo de firmware/arquivo adversarial. Cadência espelha a regra do Strava.
    private static final int CADENCIA_MIN_PPM = 60;
    private static final int CADENCIA_MAX_PPM = 200;
    private static final int ELEVACAO_MAX_METROS = 10_000;
    private static final int POTENCIA_MAX_WATTS = 2_500;

    // Running dynamics (design D2 de fit-running-dynamics-ingestion) — mesma lógica de descarte
    // silencioso; temperatura, tempo em movimento e calorias não têm faixa de sanidade própria
    // no design (passam direto, sem fabricar) — nenhum dos dois tem risco de overflow de coluna
    // (temperatura vem de Byte, sempre cabe em NUMERIC(4,1); tempo em movimento/calorias não são
    // NUMERIC). Oscilação e proporção vertical ganharam faixa aqui (achado do QA gate, 2026-07-13):
    // sem cap, um valor adversarial (a FIT entrega oscilação em mm, uint16 até 6553,5cm convertido)
    // estoura NUMERIC(4,1) (máx 999,9) e derruba a transação inteira do import por causa de um
    // único campo opcional.
    private static final int GCT_MIN_MS = 100;
    private static final int GCT_MAX_MS = 500;
    private static final BigDecimal GCT_EQUILIBRIO_MIN_PCT = BigDecimal.valueOf(30.0);
    private static final BigDecimal GCT_EQUILIBRIO_MAX_PCT = BigDecimal.valueOf(70.0);
    private static final BigDecimal PASSADA_MIN_M = BigDecimal.valueOf(0.3);
    private static final BigDecimal PASSADA_MAX_M = BigDecimal.valueOf(3.0);
    private static final BigDecimal OSCILACAO_MAX_CM = BigDecimal.valueOf(50.0);
    private static final BigDecimal PROPORCAO_MAX_PCT = BigDecimal.valueOf(50.0);

    private final AtletaRepository atletaRepository;
    private final TreinoMapper treinoMapper;
    private final IngestaoTreinoRealizadoService ingestaoTreinoRealizadoService;

    /**
     * Persiste o treino a partir dos dados já extraídos de um .fit, com dedup idempotente por
     * (externalId, atletaId).
     *
     * <p><b>Idempotent:</b> YES — reenviar o mesmo .fit retorna o registro já existente
     * ({@link FitImportResultado#novo()} {@code == false}) em vez de duplicar.
     * <p><b>Side Effects:</b> Database insert (treino + etapas) quando ainda não existe e esta
     * requisição venceu a corrida de concorrência; apenas leitura nos demais casos.
     * <p><b>Tenant-aware:</b> YES — via {@link TenantContext#getRequiredTenantId()}.
     *
     * @throws DomainNotFoundException se o atleta não for encontrado no tenant atual
     */
    @Transactional
    public FitImportResultado persistir(UUID atletaId, FitSessionData dados) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        Atleta atleta = atletaRepository.findByIdAndTenantId(atletaId, tenantId)
                .orElseThrow(() -> new DomainNotFoundException("Atleta não encontrado no tenant atual"));

        String externalId = buildExternalId(atletaId, dados);
        TreinoRealizado treino = montarTreino(dados, atleta, tenantId, externalId);

        // Dedup, tssCalculado (quando ausente), evento de análise e carga do dia são
        // responsabilidade do seam único de ingestão (ingestao-treino-realizado, D1-D13) — este
        // persister só monta a entidade a partir do .fit já parseado.
        TreinoDedupHelper.SaveResult resultado = ingestaoTreinoRealizadoService.registrar(treino, externalId);

        if (resultado.inserted()) {
            log.info("Fit importado: treinoId={}, atletaId={}, externalId={}",
                    resultado.treino().getId(), atletaId, externalId);
        } else {
            log.info("Fit já importado ou corrida de concorrência — registro já existente: treinoId={}, atletaId={}, externalId={}",
                    resultado.treino().getId(), atletaId, externalId);
        }

        return new FitImportResultado(treinoMapper.toOutputDto(resultado.treino()), resultado.inserted());
    }

    /**
     * Inclui o atletaId no externalId — a constraint real de dedup é
     * UNIQUE(tenant_id, fonte_dados, external_id), escopada por TENANT, não por atleta (D0.2).
     * Sem isso, dois atletas do mesmo tenant com dispositivos que produzam o mesmo
     * serialNumber+startTime colidiriam na constraint.
     */
    private String buildExternalId(UUID atletaId, FitSessionData dados) {
        long serial = dados.serialNumber() != null ? dados.serialNumber() : 0L;
        return atletaId + "-" + serial + "-" + dados.startTimeEpochSeconds();
    }

    private TreinoRealizado montarTreino(FitSessionData dados, Atleta atleta, UUID tenantId, String externalId) {
        TreinoRealizado treino = new TreinoRealizado();
        treino.setAtleta(atleta);
        treino.setTenantId(tenantId);
        treino.setDataTreino(dados.dataTreino());
        treino.setDiaSemana(Utils.converterDayOfWeekParaDiaSemana(dados.dataTreino().getDayOfWeek()));
        // TipoTreino não discrimina esporte (só propósito de treino de corrida) — nunca fabricar
        // INTERVALADO/TIRO/etc. a partir da estrutura do .fit (D0.6).
        treino.setTipoTreino(TipoTreino.CONTINUO);
        treino.setDescricao(descricaoParaEsporte(dados));
        treino.setDuracaoMin(dados.duracao());
        treino.setDistanciaKm(toBigDecimal(dados.distanciaKm()));
        treino.setFcMedia(dados.fcMedia());
        treino.setFcMax(dados.fcMax());
        treino.setFonteDados(FonteDados.GARMIN);
        treino.setStatus(TreinoExecucaoStatus.REALIZADO);
        treino.setCriadoPor(CRIADO_POR_GARMIN);
        treino.setExternalId(externalId);
        treino.setElevacaoGanhoMetros(sanitizarElevacao(dados.subidaMetros()));
        treino.setElevacaoPerdaMetros(sanitizarElevacao(dados.descidaMetros()));
        treino.setPotenciaMedia(sanitizarPotencia(dados.potenciaMediaWatts()));
        treino.setCadenciaMedia(sanitizarCadencia(dados.cadenciaMediaPpm()));
        treino.setTempoMovimento(dados.tempoMovimento());
        treino.setCalorias(dados.calorias());
        treino.setGctMedioMs(sanitizarGct(dados.gctMedioMs()));
        treino.setGctEquilibrioPct(sanitizarGctEquilibrio(dados.gctEquilibrioPct()));
        treino.setPassadaMediaM(sanitizarPassada(dados.passadaMediaM()));
        treino.setOscilacaoVerticalCm(sanitizarOscilacao(dados.oscilacaoVerticalCm()));
        treino.setProporcaoVerticalPct(sanitizarProporcao(dados.proporcaoVerticalPct()));
        treino.setTemperaturaMediaC(dados.temperaturaMediaC());

        if (dados.tssCalculado() != null) {
            treino.setTssCalculado(dados.tssCalculado());
            treino.setMetodoCalculoTss("DISPOSITIVO");
        }
        // Senão, deixa tssCalculado nulo — IngestaoTreinoRealizadoService.registrar calcula via
        // FC/duração quando não é DISPOSITIVO (D3.1). Pode divergir do TSS do próprio dispositivo
        // — esperado, é o mesmo comportamento de antes, só que centralizado no ingestor.

        for (FitLapData lap : dados.laps()) {
            EtapaRealizada etapa = EtapaRealizada.builder()
                    .treinoRealizado(treino)
                    .ordem(lap.ordem())
                    .splitIndex(lap.ordem())
                    .descricao("Lap " + lap.ordem())
                    .duracao(lap.duracao())
                    .distanciaKm(toBigDecimal(lap.distanciaKm()))
                    .fcMedia(lap.fcMedia())
                    .fcMax(lap.fcMax())
                    .velocidadeMedia(velocidadeMediaKmh(lap))
                    .paceMedia(paceMedia(lap))
                    .elevacaoGanhoMetros(sanitizarElevacao(lap.subidaMetros()))
                    .elevacaoPerdaMetros(sanitizarElevacao(lap.descidaMetros()))
                    .potenciaMedia(sanitizarPotencia(lap.potenciaMediaWatts()))
                    .cadenciaMedia(sanitizarCadencia(lap.cadenciaMediaPpm()))
                    .tempoMovimento(lap.tempoMovimento())
                    .gctMedioMs(sanitizarGct(lap.gctMedioMs()))
                    .gctEquilibrioPct(sanitizarGctEquilibrio(lap.gctEquilibrioPct()))
                    .passadaMediaM(sanitizarPassada(lap.passadaMediaM()))
                    .oscilacaoVerticalCm(sanitizarOscilacao(lap.oscilacaoVerticalCm()))
                    .proporcaoVerticalPct(sanitizarProporcao(lap.proporcaoVerticalPct()))
                    .temperaturaMediaC(lap.temperaturaMediaC())
                    .build();
            treino.getEtapasRealizadas().add(etapa);
        }

        return treino;
    }

    private String descricaoParaEsporte(FitSessionData dados) {
        if (dados.corrida()) {
            return "Importado de .fit";
        }
        return "Importado de .fit — esporte: " + dados.esporteDetectado();
    }

    private BigDecimal toBigDecimal(Double valor) {
        return valor != null ? BigDecimal.valueOf(valor).setScale(3, RoundingMode.HALF_UP) : null;
    }

    /**
     * O .fit traz só distância/duração por lap — velocidade e pace são derivados aqui para que
     * a etapa persistida tenha o mesmo contrato dos splits do Strava (que já chegam com
     * velocidade pronta). Sem isso o {@code DecouplingCalculatorService} descarta todas as
     * etapas de imports .fit e o decoupling Pa:HR volta sempre null.
     */
    private static BigDecimal velocidadeMediaKmh(FitLapData lap) {
        if (!temMetricaDeVelocidade(lap)) {
            return null;
        }
        double horas = duracaoParaVelocidade(lap).toMillis() / 3_600_000.0;
        return BigDecimal.valueOf(lap.distanciaKm() / horas).setScale(2, RoundingMode.HALF_UP);
    }

    private static Duration paceMedia(FitLapData lap) {
        if (!temMetricaDeVelocidade(lap)) {
            return null;
        }
        return Duration.ofSeconds(Math.round(duracaoParaVelocidade(lap).toSeconds() / lap.distanciaKm()));
    }

    /**
     * Design D6 de fit-running-dynamics-ingestion (CA7): corrige pace/velocidade em laps com
     * pausa. {@code duracao} ({@code totalElapsedTime}) inclui o tempo parado; quando o lap tem
     * {@code tempoMovimento} ({@code totalTimerTime}) menor, ele é a base real do esforço — sem
     * isso, pace/velocidade ficam artificialmente lentos em laps com autopause/pausa manual
     * (achado registrado por fit-lap-derived-metrics: até 239 s/km de desvio numa fixture real).
     * Não muda {@code EtapaRealizada.duracao} em si, só o cálculo derivado desta classe.
     */
    private static Duration duracaoParaVelocidade(FitLapData lap) {
        Duration movimento = lap.tempoMovimento();
        boolean movimentoValido = movimento != null && !movimento.isZero() && !movimento.isNegative()
                && movimento.compareTo(lap.duracao()) < 0;
        return movimentoValido ? movimento : lap.duracao();
    }

    private static boolean temMetricaDeVelocidade(FitLapData lap) {
        return lap.distanciaKm() != null && lap.distanciaKm() > 0
                && lap.duracao() != null && !lap.duracao().isZero() && !lap.duracao().isNegative();
    }

    private static Integer sanitizarCadencia(Integer ppm) {
        if (ppm == null || ppm < CADENCIA_MIN_PPM || ppm > CADENCIA_MAX_PPM) {
            return null;
        }
        return ppm;
    }

    /** Elevação por lap/sessão: 0 é válido (percurso plano); acima do teto é dado adversarial. */
    private static Integer sanitizarElevacao(Integer metros) {
        if (metros == null || metros < 0 || metros > ELEVACAO_MAX_METROS) {
            return null;
        }
        return metros;
    }

    /** Potência média: 0 W equivale a "sem dado" (sensor ausente), não a um treino real. */
    private static Integer sanitizarPotencia(Integer watts) {
        if (watts == null || watts <= 0 || watts > POTENCIA_MAX_WATTS) {
            return null;
        }
        return watts;
    }

    /** Tempo médio de contato com o solo: fora de 100-500ms é lixo de firmware, não corrida real. */
    private static Integer sanitizarGct(Integer ms) {
        if (ms == null || ms < GCT_MIN_MS || ms > GCT_MAX_MS) {
            return null;
        }
        return ms;
    }

    /** Equilíbrio de GCT (% do pé esquerdo): fora de 30-70% é fisiologicamente implausível. */
    private static BigDecimal sanitizarGctEquilibrio(BigDecimal pct) {
        if (pct == null || pct.compareTo(GCT_EQUILIBRIO_MIN_PCT) < 0 || pct.compareTo(GCT_EQUILIBRIO_MAX_PCT) > 0) {
            return null;
        }
        return pct;
    }

    /** Comprimento de passada: fora de 0,3-3,0m é lixo de firmware, não passada de corrida real. */
    private static BigDecimal sanitizarPassada(BigDecimal metros) {
        if (metros == null || metros.compareTo(PASSADA_MIN_M) < 0 || metros.compareTo(PASSADA_MAX_M) > 0) {
            return null;
        }
        return metros;
    }

    /**
     * Oscilação vertical: negativa ou acima do teto de sanidade é lixo de firmware. Guarda
     * também contra overflow de {@code NUMERIC(4,1)} (máx 999,9) — a FIT entrega o dado bruto em
     * mm (uint16, até 6553,5cm convertido); sem cap, um valor adversarial derrubaria a transação
     * inteira do import na constraint do banco em vez de simplesmente descartar o campo opcional.
     */
    private static BigDecimal sanitizarOscilacao(BigDecimal cm) {
        if (cm == null || cm.signum() < 0 || cm.compareTo(OSCILACAO_MAX_CM) > 0) {
            return null;
        }
        return cm;
    }

    /** Proporção vertical: mesma lógica e mesmo motivo de {@link #sanitizarOscilacao}. */
    private static BigDecimal sanitizarProporcao(BigDecimal pct) {
        if (pct == null || pct.signum() < 0 || pct.compareTo(PROPORCAO_MAX_PCT) > 0) {
            return null;
        }
        return pct;
    }
}
