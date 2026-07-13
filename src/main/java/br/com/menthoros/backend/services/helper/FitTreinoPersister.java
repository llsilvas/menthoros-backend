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
import br.com.menthoros.backend.events.TreinoRegistradoEvent;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.mapper.TreinoMapper;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.TsbService;
import br.com.menthoros.backend.util.Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Optional;
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

    private final AtletaRepository atletaRepository;
    private final TreinoRealizadoRepository treinoRealizadoRepository;
    private final TreinoMapper treinoMapper;
    private final TsbService tsbService;
    private final TssCalculatorService tssCalculatorService;
    private final ApplicationEventPublisher eventPublisher;
    private final TreinoDedupHelper treinoDedupHelper;

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

        Optional<TreinoRealizado> existente = treinoRealizadoRepository
                .findByExternalIdAndAtletaId(externalId, atletaId);
        if (existente.isPresent()) {
            log.info("Fit já importado anteriormente: externalId={}, atletaId={}", externalId, atletaId);
            return new FitImportResultado(treinoMapper.toOutputDto(existente.get()), false);
        }

        TreinoRealizado treino = montarTreino(dados, atleta, tenantId, externalId);
        TreinoDedupHelper.SaveResult resultado = treinoDedupHelper.saveIdempotent(treino, externalId, atletaId);
        TreinoRealizado salvo = resultado.treino();

        // Publicar evento/recalcular TSB só faz sentido quando ESTA requisição de fato inseriu o
        // treino — do contrário duplicaríamos o efeito colateral para um treino já processado por
        // outra requisição concorrente (ver TreinoDedupHelper.SaveResult#inserted). O flag "novo"
        // retornado ao controller (200 vs 201) usa o MESMO sinal — nunca hardcoded.
        if (resultado.inserted()) {
            eventPublisher.publishEvent(new TreinoRegistradoEvent(salvo.getId(), tenantId));
            tsbService.atualizarTsbDia(atletaId, salvo.getDataTreino());
            log.info("Fit importado: treinoId={}, atletaId={}, externalId={}", salvo.getId(), atletaId, externalId);
        } else {
            log.info("Corrida de concorrência no import de .fit — registro já persistido por outra requisição: treinoId={}, atletaId={}, externalId={}",
                    salvo.getId(), atletaId, externalId);
        }

        return new FitImportResultado(treinoMapper.toOutputDto(salvo), resultado.inserted());
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

        if (dados.tssCalculado() != null) {
            treino.setTssCalculado(dados.tssCalculado());
            treino.setMetodoCalculoTss("DISPOSITIVO");
        } else {
            // TSS ausente no .fit (dispositivo mais antigo ou não-Garmin) — calcula via FC/duração
            // já preenchidos no treino (D0.3). Pode divergir do TSS do próprio dispositivo — esperado.
            treino.setTssCalculado(tssCalculatorService.calcularTss(treino));
            treino.setMetodoCalculoTss("FC");
        }

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
        double horas = lap.duracao().toMillis() / 3_600_000.0;
        return BigDecimal.valueOf(lap.distanciaKm() / horas).setScale(2, RoundingMode.HALF_UP);
    }

    private static Duration paceMedia(FitLapData lap) {
        if (!temMetricaDeVelocidade(lap)) {
            return null;
        }
        return Duration.ofSeconds(Math.round(lap.duracao().toSeconds() / lap.distanciaKm()));
    }

    private static boolean temMetricaDeVelocidade(FitLapData lap) {
        return lap.distanciaKm() != null && lap.distanciaKm() > 0
                && lap.duracao() != null && !lap.duracao().isZero() && !lap.duracao().isNegative();
    }
}
