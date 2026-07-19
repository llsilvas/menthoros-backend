package br.com.menthoros.backend.domain.planner;

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
public class LoadTargetResolver {

    private static final double MAX_CTL_RAMP_PER_WEEK = 8.0; // CA1
    private static final int SEMANAS_PARA_STEP_BACK = 3; // CA2 — na 4a semana consecutiva
    private static final double STEP_BACK_FACTOR = 0.80; // reducao de 20%, dentro da banda 15-25%
    private static final double FAIXA_TOLERANCIA = 0.10;

    public WeeklyLoadTarget resolve(TrainingPhase phase, DecisaoProgressao decisao, ProgressaoHistoricoResumo historico) {
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

    private boolean isFaseDeContencao(TrainingPhase phase) {
        return phase == TrainingPhase.TAPER
                || phase == TrainingPhase.RACE_WEEK
                || phase == TrainingPhase.RECOVERY
                || phase == TrainingPhase.POST_RACE
                || phase == TrainingPhase.RETURN_TO_TRAINING;
    }
}
