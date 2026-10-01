package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.DecisaoProgressao;
import br.com.menthoros.backend.dto.ProgressaoHistoricoResumo;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.EstadoProgressao;
import br.com.menthoros.backend.enums.ReconciliationStatus;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.TreinoPlanejadoRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.PlanoMetadadosService;
import br.com.menthoros.backend.services.ProgressaoTreinoService;
import br.com.menthoros.backend.services.helper.AtletaHojeResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProgressaoTreinoServiceImpl implements ProgressaoTreinoService {

    private static final Set<TipoTreino> TREINOS_DUROS = Set.of(
            TipoTreino.INTERVALADO, TipoTreino.TIRO, TipoTreino.TEMPO_RUN, TipoTreino.SUBIDA
    );

    private static final double THRESHOLD_ADERENCIA_PROGREDIR = 0.80;
    private static final double THRESHOLD_ADERENCIA_PROGREDIR_LEVE = 0.70;
    private static final double THRESHOLD_ADERENCIA_REDUZIR = 0.60;
    private static final double THRESHOLD_TSB_PROGREDIR = -15.0;
    private static final double THRESHOLD_TSB_REDUZIR = -22.0;
    private static final double THRESHOLD_RPE_LIMITE = 7.5;
    private static final double THRESHOLD_RPE_REDUZIR = 8.5;
    private static final int LONGAS_MINIMAS_PROGREDIR = 2;
    private static final int TREINOS_MINIMOS_21D = 3;
    private static final double TETO_PENDENCIA = 0.25;
    private static final String ATOR_SISTEMA = "SYSTEM";

    private static final double AJUSTE_VOLUME_PROGREDIR      =  0.06;
    private static final double AJUSTE_VOLUME_PROGREDIR_LEVE =  0.03;
    private static final double AJUSTE_VOLUME_REDUZIR        = -0.05;
    private static final int    AJUSTE_LONGO_PROGREDIR       =  10;
    private static final int    AJUSTE_LONGO_PROGREDIR_LEVE  =   5;
    private static final int    AJUSTE_LONGO_REDUZIR         = -10;

    private final TreinoRealizadoRepository treinoRealizadoRepository;
    private final TreinoPlanejadoRepository treinoPlanejadoRepository;
    private final AtletaRepository atletaRepository;
    private final AtletaHojeResolver atletaHojeResolver;
    private final PlanoMetadadosService planoMetadadosService;
    private final Clock clock;

    @Value("${menthoros.progressao.aderencia-devidos.enabled:false}")
    private boolean aderenciaDevidosEnabled;

    @Override
    @Transactional(readOnly = true)
    public ProgressaoHistoricoResumo calcularHistorico(UUID atletaId) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        Atleta atleta = atletaRepository.findByIdAndTenantId(atletaId, tenantId)
                .orElseThrow(() -> new DomainNotFoundException("Atleta não encontrado"));
        LocalDate hoje = atletaHojeResolver.hojeDe(atleta);
        LocalDate inicio7d = hoje.minusDays(7);
        LocalDate inicio21d = hoje.minusDays(21);
        LocalDate inicio42d = hoje.minusDays(42);

        // D8 (ingestao-treino-realizado): cancelado não conta na carga — mesmo predicado usado por
        // TsbService/produtores; achado do /qa do Bloco 2 (Codex adversarial-review, 2026-08-24) —
        // esta query alimenta a decisão de progressão do plano (volume/longão/RPE) e ficara de fora
        // do inventário original da task 7.7. Janela de 42/21/7 dias corridos até hoje — fora do
        // escopo de fix-progression-adherence-window (só a aderência usa a janela nova, abaixo).
        List<TreinoRealizado> treinos42d = treinoRealizadoRepository
                .findByAtletaIdAndTenantIdAndDataTreinoBetween(atletaId, tenantId, inicio42d, hoje).stream()
                .filter(TreinoRealizado::contaNaCarga)
                .toList();

        List<TreinoRealizado> treinos21d = treinos42d.stream()
                .filter(t -> !t.getDataTreino().isBefore(inicio21d))
                .toList();

        List<TreinoRealizado> treinos7d = treinos42d.stream()
                .filter(t -> !t.getDataTreino().isBefore(inicio7d))
                .toList();

        double volumeKm42d = calcularVolumeKm(treinos42d);
        double volumeKm21d = calcularVolumeKm(treinos21d);
        double volumeKm7d = calcularVolumeKm(treinos7d);

        int longoesRealizados21d = contarLongoes(treinos21d);
        int longoesRealizados7d = contarLongoes(treinos7d);

        Double rpeMedioTreinosDuros = calcularRpeMedioTreinosDuros(treinos21d);

        int treinosRealizados21d = treinos21d.size();

        AderenciaJanela aderenciaJanela = aderenciaDevidosEnabled
                ? calcularAderenciaJanelaFechada(atletaId, tenantId, hoje)
                : calcularAderenciaRegraAntiga(atletaId, tenantId, inicio21d, hoje, treinosRealizados21d);

        PlanoMetaDados metaDados = planoMetadadosService.buscarPorAtletaId(atletaId);

        log.debug("Histórico calculado para atleta {}: realizados21d={}, cumpridos={}, faltas={}, " +
                        "pendentes={}, aderencia={}, tsb={}",
                atletaId, treinosRealizados21d, aderenciaJanela.cumpridos(), aderenciaJanela.faltas(),
                aderenciaJanela.pendentes(), aderenciaJanela.aderencia(), metaDados.getTsbAtual());

        return new ProgressaoHistoricoResumo(
                treinosRealizados21d,
                volumeKm7d, volumeKm21d, volumeKm42d,
                longoesRealizados7d, longoesRealizados21d,
                rpeMedioTreinosDuros,
                metaDados.getTsbAtual(), metaDados.getCtlAtual(), metaDados.getAtlAtual(),
                metaDados.getSemanasProgressaoContinua() != null ? metaDados.getSemanasProgressaoContinua() : 0,
                aderenciaJanela.cumpridos(), aderenciaJanela.faltas(), aderenciaJanela.pendentes(),
                aderenciaJanela.aderencia()
        );
    }

    /**
     * D1/D2 (fix-progression-adherence-window): aderência medida sobre as 3 semanas ISO fechadas
     * antes da atual — a semana em curso fica inteiramente fora, então "hoje só se feito" não se
     * aplica aqui (todo planejado da janela já venceu).
     */
    private AderenciaJanela calcularAderenciaJanelaFechada(UUID atletaId, UUID tenantId, LocalDate hoje) {
        LocalDate segundaAtual = hoje.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate inicioJanela = segundaAtual.minusDays(21);
        LocalDate fimJanela = segundaAtual.minusDays(1);

        List<TreinoPlanejado> planejadosJanela = treinoPlanejadoRepository
                .findComRealizadoByAtletaAndPeriodoAteData(atletaId, tenantId, inicioJanela, fimJanela);
        List<TreinoRealizado> realizadosJanela = treinoRealizadoRepository
                .findByAtletaIdAndTenantIdAndDataTreinoBetween(atletaId, tenantId, inicioJanela, fimJanela);

        Map<LocalDate, List<TreinoRealizado>> avulsosPorData = realizadosJanela.stream()
                .filter(r -> r.getTreinoPlanejado() == null)
                .collect(Collectors.groupingBy(TreinoRealizado::getDataTreino));

        int cumpridos = 0;
        int faltas = 0;
        int pendentes = 0;
        for (TreinoPlanejado planejado : planejadosJanela) {
            if (!isDevido(planejado)) {
                continue; // DESCANSO fora da conta (D2)
            }
            TreinoRealizado vinculado = planejado.getTreinoRealizado();
            if (vinculado != null) {
                if (vinculado.contaNaCarga()) {
                    cumpridos++;
                } else {
                    faltas++; // vínculo sobrevive ao cancelamento no Strava — não é cumprimento (CA10)
                }
                continue;
            }
            List<TreinoRealizado> avulsosNoDia = avulsosPorData.getOrDefault(planejado.getDataTreino(), List.of());
            if (avulsosNoDia.isEmpty()) {
                faltas++;
            } else if (temTriagemHumanaDeNaoCorrespondencia(avulsosNoDia)) {
                faltas++; // triagem humana já resolveu a ambiguidade (CA11)
            } else {
                pendentes++; // dado incompleto ou triagem automática (CA4/CA12) — fora da conta
            }
        }

        return new AderenciaJanela(cumpridos, faltas, pendentes, calcularAderenciaComTeto(cumpridos, faltas, pendentes));
    }

    /** D7: flag desligada reproduz a regra antiga (CA8) — sem filtro de DESCANSO/pendência/cancelado. */
    private AderenciaJanela calcularAderenciaRegraAntiga(UUID atletaId, UUID tenantId, LocalDate inicio21d,
                                                          LocalDate hoje, int treinosRealizados21d) {
        int treinosPlanejados21d = treinoPlanejadoRepository
                .findComRealizadoByAtletaAndPeriodoAteData(atletaId, tenantId, inicio21d, hoje).size();
        double aderencia = treinosPlanejados21d == 0 ? 0.0 : (double) treinosRealizados21d / treinosPlanejados21d;
        int cumpridos = Math.min(treinosRealizados21d, treinosPlanejados21d);
        int faltas = Math.max(0, treinosPlanejados21d - treinosRealizados21d);
        return new AderenciaJanela(cumpridos, faltas, 0, aderencia);
    }

    private static boolean isDevido(TreinoPlanejado planejado) {
        return planejado.getTipoTreino() != TipoTreino.DESCANSO;
    }

    private static boolean temTriagemHumanaDeNaoCorrespondencia(List<TreinoRealizado> avulsos) {
        return avulsos.stream().anyMatch(r ->
                r.getReconciliationStatus() == ReconciliationStatus.NAO_PLANEJADO
                        && !ATOR_SISTEMA.equals(r.getReconciledBy()));
    }

    /** null (ausente) quando não há devido na janela ou a pendência passa de 25% dos devidos (D2). */
    private static Double calcularAderenciaComTeto(int cumpridos, int faltas, int pendentes) {
        int devidos = cumpridos + faltas + pendentes;
        if (devidos == 0) return null;
        if ((double) pendentes / devidos > TETO_PENDENCIA) return null;
        return (double) cumpridos / (cumpridos + faltas);
    }

    private record AderenciaJanela(int cumpridos, int faltas, int pendentes, Double aderencia) {}

    @Override
    public DecisaoProgressao calcularDecisao(ProgressaoHistoricoResumo resumo) {
        if (resumo.treinosRealizados21d() < TREINOS_MINIMOS_21D) {
            log.debug("Histórico insuficiente ({} treinos em 21d) — retornando MANTER", resumo.treinosRealizados21d());
            return new DecisaoProgressao(EstadoProgressao.MANTER, 0.0, 0, false, "histórico insuficiente");
        }

        double tsb = resumo.tsbAtual() != null ? resumo.tsbAtual() : 0.0;
        Double rpe = resumo.rpeMedioTreinosDuros();
        Double aderencia = resumo.aderencia();

        if (aderencia == null) {
            // D3: aderência ausente nunca libera PROGREDIR/PROGREDIR_LEVE; REDUZIR só por fadiga
            // comprovada (o histórico mínimo acima já garantiu >= 3 realizados em 21d).
            if (temFadiga(tsb, rpe)) {
                String motivo = motivoFadigaSemAderencia(tsb, rpe);
                log.info("DecisaoProgressao REDUZIR sem aderência — motivo: {}", motivo);
                return new DecisaoProgressao(EstadoProgressao.REDUZIR, AJUSTE_VOLUME_REDUZIR, AJUSTE_LONGO_REDUZIR, false, motivo);
            }
            log.debug("DecisaoProgressao MANTER — aderência ausente, sem fadiga");
            return new DecisaoProgressao(EstadoProgressao.MANTER, 0.0, 0, false, "aderência ausente, sem fadiga");
        }

        if (deveReduzir(aderencia, tsb, rpe)) {
            String motivo = motivoReducao(aderencia, tsb, rpe);
            log.info("DecisaoProgressao REDUZIR para histórico — motivo: {}", motivo);
            return new DecisaoProgressao(EstadoProgressao.REDUZIR, AJUSTE_VOLUME_REDUZIR, AJUSTE_LONGO_REDUZIR, false, motivo);
        }

        if (podeProgredir(aderencia, tsb, rpe, resumo.longoesRealizados21d())) {
            log.info("DecisaoProgressao PROGREDIR — aderência={}, longões21d={}, TSB={}",
                    aderencia, resumo.longoesRealizados21d(), tsb);
            return new DecisaoProgressao(EstadoProgressao.PROGREDIR, AJUSTE_VOLUME_PROGREDIR, AJUSTE_LONGO_PROGREDIR, true,
                    "atleta respondendo bem ao treino");
        }

        if (podeProgredirLeve(aderencia, tsb)) {
            log.info("DecisaoProgressao PROGREDIR_LEVE — aderência={}, TSB={}", aderencia, tsb);
            return new DecisaoProgressao(EstadoProgressao.PROGREDIR_LEVE, AJUSTE_VOLUME_PROGREDIR_LEVE, AJUSTE_LONGO_PROGREDIR_LEVE, false,
                    "progressão moderada recomendada");
        }

        log.debug("DecisaoProgressao MANTER — aderência={}, TSB={}", aderencia, tsb);
        return new DecisaoProgressao(EstadoProgressao.MANTER, 0.0, 0, false, "manter volume atual");
    }

    private boolean temFadiga(double tsb, Double rpe) {
        return tsb < THRESHOLD_TSB_REDUZIR || (rpe != null && rpe > THRESHOLD_RPE_REDUZIR);
    }

    private String motivoFadigaSemAderencia(double tsb, Double rpe) {
        if (tsb < THRESHOLD_TSB_REDUZIR) return String.format("TSB crítico (%.1f), aderência ausente", tsb);
        return String.format("RPE médio elevado (%.1f), aderência ausente", rpe);
    }

    private boolean deveReduzir(double aderencia, double tsb, Double rpe) {
        return tsb < THRESHOLD_TSB_REDUZIR
                || aderencia < THRESHOLD_ADERENCIA_REDUZIR
                || (rpe != null && rpe > THRESHOLD_RPE_REDUZIR);
    }

    private boolean podeProgredir(double aderencia, double tsb, Double rpe, int longoes21d) {
        return aderencia >= THRESHOLD_ADERENCIA_PROGREDIR
                && longoes21d >= LONGAS_MINIMAS_PROGREDIR
                && tsb > THRESHOLD_TSB_PROGREDIR
                && (rpe == null || rpe <= THRESHOLD_RPE_LIMITE);
    }

    private boolean podeProgredirLeve(double aderencia, double tsb) {
        return aderencia >= THRESHOLD_ADERENCIA_PROGREDIR_LEVE
                && tsb > THRESHOLD_TSB_REDUZIR;
    }

    private String motivoReducao(double aderencia, double tsb, Double rpe) {
        if (tsb < THRESHOLD_TSB_REDUZIR) return String.format("TSB crítico (%.1f)", tsb);
        if (aderencia < THRESHOLD_ADERENCIA_REDUZIR) return "aderência abaixo de 60%";
        return String.format("RPE médio elevado (%.1f)", rpe);
    }

    private double calcularVolumeKm(List<TreinoRealizado> treinos) {
        return treinos.stream()
                .filter(t -> t.getDistanciaKm() != null)
                .mapToDouble(t -> t.getDistanciaKm().doubleValue())
                .sum();
    }

    private int contarLongoes(List<TreinoRealizado> treinos) {
        return (int) treinos.stream()
                .filter(t -> TipoTreino.LONGO.equals(t.getTipoTreinoEfetivo()))
                .count();
    }

    private Double calcularRpeMedioTreinosDuros(List<TreinoRealizado> treinos) {
        OptionalDouble media = treinos.stream()
                .filter(t -> TREINOS_DUROS.contains(t.getTipoTreinoEfetivo()))
                .filter(t -> t.getPercepcaoEsforco() != null)
                .mapToInt(TreinoRealizado::getPercepcaoEsforco)
                .average();
        return media.isPresent() ? media.getAsDouble() : null;
    }
}
