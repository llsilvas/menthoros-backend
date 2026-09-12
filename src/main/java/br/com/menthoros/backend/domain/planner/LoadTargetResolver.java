package br.com.menthoros.backend.domain.planner;

import org.springframework.stereotype.Component;

import br.com.menthoros.backend.dto.DecisaoProgressao;
import br.com.menthoros.backend.dto.ProgressaoHistoricoResumo;
import br.com.menthoros.backend.enums.EstadoProgressao;

/**
 * Resolve o {@link WeeklyLoadTarget} combinando fase, taper (fases de contencao) e
 * {@code DecisaoProgressao} (design.md Decisao 6). Nao recalcula a direcao de progressao —
 * {@code ProgressaoTreinoService} permanece fora, como input.
 *
 * <p>Baseline: consome executado, nao planejado (Decisao 5) — {@code ctlAtual} ja reflete o
 * TSS real recente via {@code TsbService}. O alvo semanal parte de {@code ctlAtual * 7}
 * (mesma aproximacao ja usada em {@code PeriodizacaoPromptFormatter.calcularTssAlvo}).
 */
@Component
public class LoadTargetResolver {

    private static final double MAX_CTL_RAMP_PER_WEEK = 8.0; // CA1
    private static final int SEMANAS_PARA_STEP_BACK = 3; // CA2 — na 4a semana consecutiva
    private static final double STEP_BACK_FACTOR = 0.80; // reducao de 20%, dentro da banda 15-25%
    private static final double FAIXA_TOLERANCIA = 0.10;

    // Regime cold-start (ADR-0012) — atleta em calibracao, sem PMC confiavel.
    private static final double TETO_CTL_COLD_START = 40.0; // cap contra autodeclaracao inflada
    private static final double PISO_COLD_START = 120.0; // TSS/sem, so em fase progressiva
    private static final double BANDA_COLD_START = 0.25; // +-25% (vs +-10% do caminho normal)
    private static final double RAMPA_OBSERVATION = 0.60;
    private static final double RAMPA_CALIBRATION = 0.75;
    private static final double RAMPA_STABILIZATION = 0.90;

    /**
     * Caminho normal (PMC): atleta graduado da calibracao ou legado. Delega com {@code null},
     * mantendo o comportamento historico (banda +-10%, teto de rampa por CTL).
     */
    public WeeklyLoadTarget resolve(TrainingPhase phase, DecisaoProgressao decisao, ProgressaoHistoricoResumo historico) {
        return resolve(phase, decisao, historico, null, null);
    }

    /**
     * Regime cold-start quando {@code calibrationStage != null} (ADR-0012): o alvo parte do CTL de
     * baseline capado em 40, rampado por estagio, com piso 120 (so em fase progressiva) e banda
     * +-25%. Ausente, cai no caminho normal (CTL de PMC).
     */
    public WeeklyLoadTarget resolve(TrainingPhase phase, DecisaoProgressao decisao,
            ProgressaoHistoricoResumo historico, CalibrationStage calibrationStage, Double ctlBaseline) {
        if (calibrationStage != null) {
            return resolveColdStart(phase, calibrationStage, ctlBaseline, decisao);
        }
        double ctlAtual = historico.ctlAtual() != null ? historico.ctlAtual() : 0.0;
        double baselineWeeklyTss = ctlAtual * 7;

        double ajuste = decisao.ajusteVolumePercentual();
        boolean permiteAumento = decisao.estado() != EstadoProgressao.REDUZIR;
        if (!permiteAumento && ajuste > 0) {
            ajuste = 0; // REDUZIR nunca vira aumento, mesmo com dado de entrada inconsistente
        }

        double targetWeeklyTss = baselineWeeklyTss * (1 + ajuste);

        if (permiteAumento) {
            double tetoDeRampa = 7 * (ctlAtual + MAX_CTL_RAMP_PER_WEEK); // CA1
            targetWeeklyTss = Math.min(targetWeeklyTss, tetoDeRampa);
        }

        if (isFaseDeContencao(phase)) {
            // Prova na semana / taper / pos-prova tem precedencia sobre a progressao (hierarquia P0,
            // design.md Decisao 6) — o desenho fino da curva de reducao fica com a TaperStrategy.
            targetWeeklyTss = Math.min(targetWeeklyTss, baselineWeeklyTss);
        }

        String rationale = decisao.motivo();
        if (permiteAumento && historico.semanasProgressaoContinua() >= SEMANAS_PARA_STEP_BACK) {
            targetWeeklyTss = targetWeeklyTss * STEP_BACK_FACTOR; // CA2
            rationale = "Step-back apos " + historico.semanasProgressaoContinua()
                    + " semanas consecutivas de progressao; " + rationale;
        }

        double min = targetWeeklyTss * (1 - FAIXA_TOLERANCIA);
        double max = targetWeeklyTss * (1 + FAIXA_TOLERANCIA);

        return new WeeklyLoadTarget(targetWeeklyTss, min, max, rationale);
    }

    private WeeklyLoadTarget resolveColdStart(TrainingPhase phase, CalibrationStage stage,
            Double ctlBaseline, DecisaoProgressao decisao) {
        double ctlUsado = Math.min(ctlBaseline != null ? ctlBaseline : 0.0, TETO_CTL_COLD_START);
        double rampa = rampaPorEstagio(stage);
        double target = ctlUsado * 7 * rampa;

        boolean contencao = isFaseDeContencao(phase);
        if (!contencao && target < PISO_COLD_START) {
            target = PISO_COLD_START; // piso so em fase progressiva
        }

        double min = target * (1 - BANDA_COLD_START);
        double max = target * (1 + BANDA_COLD_START);
        String rationale = String.format(
                "Cold-start %s (CTL baseline capado %.0f, rampa %.2f); %s",
                stage, ctlUsado, rampa, decisao.motivo());
        return new WeeklyLoadTarget(target, min, max, rationale);
    }

    private double rampaPorEstagio(CalibrationStage stage) {
        return switch (stage) {
            case OBSERVATION -> RAMPA_OBSERVATION;
            case CALIBRATION -> RAMPA_CALIBRATION;
            case STABILIZATION -> RAMPA_STABILIZATION;
        };
    }

    private boolean isFaseDeContencao(TrainingPhase phase) {
        return phase == TrainingPhase.TAPER
                || phase == TrainingPhase.RACE_WEEK
                || phase == TrainingPhase.RECOVERY
                || phase == TrainingPhase.POST_RACE
                || phase == TrainingPhase.RETURN_TO_TRAINING;
    }
}
