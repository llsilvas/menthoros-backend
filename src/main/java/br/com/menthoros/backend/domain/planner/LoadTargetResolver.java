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

    static final double MAX_CTL_RAMP_PER_WEEK = 8.0; // CA1 — também teto da rampa do SeasonPlanner
    private static final int SEMANAS_PARA_STEP_BACK = 3; // CA2 — na 4a semana consecutiva
    private static final double STEP_BACK_FACTOR = 0.80; // reducao de 20%, dentro da banda 15-25%
    private static final double FAIXA_TOLERANCIA = 0.10;

    // Regime cold-start (ADR-0012) — atleta em calibracao, sem PMC confiavel.
    public static final double TETO_CTL_COLD_START = 40.0; // cap contra autodeclaracao inflada (também no SeasonPlanService)
    private static final double PISO_COLD_START = 120.0; // TSS/sem, so em fase progressiva
    private static final double BANDA_COLD_START = 0.25; // +-25% (vs +-10% do caminho normal)
    private static final double RAMPA_OBSERVATION = 0.60;
    private static final double RAMPA_CALIBRATION = 0.75;
    private static final double RAMPA_STABILIZATION = 0.90;

    // RECOVERY/POST_RACE reduzem de verdade (ADR-0012, Decisao 3) — distinto do taper por prova
    // (TaperStrategy) e multiplicativo com a rampa do cold-start.
    private static final double FATOR_REDUCAO = 0.5;

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

        if (isFaseDeReducao(phase)) {
            // RECOVERY/POST_RACE reduzem de verdade sobre o baseline (ADR-0012) — nao apenas capam.
            targetWeeklyTss = baselineWeeklyTss * FATOR_REDUCAO;
        } else if (isFaseDeContencao(phase)) {
            // Prova na semana / taper tem precedencia sobre a progressao (hierarquia P0,
            // design.md Decisao 6) — o desenho fino da curva de reducao fica com a TaperStrategy.
            targetWeeklyTss = Math.min(targetWeeklyTss, baselineWeeklyTss);
        }

        String rationale = decisao.motivo();
        if (permiteAumento && !isFaseDeReducao(phase)
                && historico.semanasProgressaoContinua() >= SEMANAS_PARA_STEP_BACK) {
            targetWeeklyTss = targetWeeklyTss * STEP_BACK_FACTOR; // CA2
            rationale = "Step-back apos " + historico.semanasProgressaoContinua()
                    + " semanas consecutivas de progressao; " + rationale;
        }

        double min = targetWeeklyTss * (1 - FAIXA_TOLERANCIA);
        double max = targetWeeklyTss * (1 + FAIXA_TOLERANCIA);

        return new WeeklyLoadTarget(targetWeeklyTss, min, max, rationale);
    }

    /**
     * Caminho da temporada (add-season-plan-load-enforcement, ADR-0014): o {@code targetTss} da semana é a
     * base e o teto; a progressão reativa e o step-back não se aplicam (a temporada já tem rampa e
     * descargas). Precedência segurança > temporada > progressão, nesta ordem:
     * redução por fase (lesão/pós-prova) → {@code REDUZIR} → teto de rampa (e contenção em taper/semana de prova)
     * → cold-start → teto da temporada.
     * Em cold-start, {@code REDUZIR} e a rampa não se aplicam (CTL de PMC não confiável na calibração).
     * Banda sobre o alvo resolvido: ±10%, ou ±25% em cold-start.
     */
    public WeeklyLoadTarget resolveSeason(TrainingPhase phase, DecisaoProgressao decisao,
            ProgressaoHistoricoResumo historico, CalibrationStage calibrationStage, Double ctlBaseline,
            double seasonTargetTss) {
        Corte corte = new Corte(seasonTargetTss);
        if (isFaseDeReducao(phase)) {
            corte.aplicar(seasonTargetTss * FATOR_REDUCAO, SeasonCutReason.REDUCAO_FASE);
        }
        double banda = FAIXA_TOLERANCIA;
        if (calibrationStage != null) {
            corte.aplicar(resolveColdStart(phase, calibrationStage, ctlBaseline, decisao).targetTss(),
                    SeasonCutReason.COLD_START);
            banda = BANDA_COLD_START;
        } else {
            double ctlAtual = historico.ctlAtual() != null ? historico.ctlAtual() : 0.0;
            if (decisao.estado() == EstadoProgressao.REDUZIR) {
                double ajuste = Math.min(decisao.ajusteVolumePercentual(), 0);
                corte.aplicar(ctlAtual * 7 * (1 + ajuste), SeasonCutReason.PROGRESSAO_REDUZIR);
            }
            corte.aplicar(7 * (ctlAtual + MAX_CTL_RAMP_PER_WEEK), SeasonCutReason.TETO_RAMPA);
            if (isFaseDeContencao(phase)) {
                // Mesma contenção do legado: semana de prova/taper não passa da carga atual, mesmo que o
                // targetTss da temporada tenha ficado acima dela.
                corte.aplicar(ctlAtual * 7, SeasonCutReason.CONTENCAO_PROVA);
            }
        }

        double alvo = corte.alvo;
        String rationale = String.format("Alvo da temporada %.0f TSS%s; %s", seasonTargetTss,
                corte.motivo == null ? "" : ", cortado para " + Math.round(alvo) + " (" + corte.motivo + ")",
                decisao.motivo());
        return new WeeklyLoadTarget(alvo, alvo * (1 - banda), alvo * (1 + banda), rationale,
                seasonTargetTss, corte.motivo);
    }

    /** Alvo que só desce; o motivo é o da última proteção que o baixou (a mais restritiva). */
    private static final class Corte {
        private double alvo;
        private SeasonCutReason motivo;

        private Corte(double base) {
            this.alvo = base;
        }

        private void aplicar(double candidato, SeasonCutReason razao) {
            if (candidato < alvo) {
                alvo = candidato;
                motivo = razao;
            }
        }
    }

    private WeeklyLoadTarget resolveColdStart(TrainingPhase phase, CalibrationStage stage,
            Double ctlBaseline, DecisaoProgressao decisao) {
        double ctlUsado = Math.min(ctlBaseline != null ? ctlBaseline : 0.0, TETO_CTL_COLD_START);
        double rampa = rampaPorEstagio(stage);
        double fatorContencao = isFaseDeReducao(phase) ? FATOR_REDUCAO : 1.0; // multiplicativo com a rampa
        double target = ctlUsado * 7 * rampa * fatorContencao;

        boolean contencao = isFaseDeContencao(phase);
        if (!contencao && target < PISO_COLD_START) {
            target = PISO_COLD_START; // piso so em fase progressiva
        }

        double min = target * (1 - BANDA_COLD_START);
        double max = target * (1 + BANDA_COLD_START);
        String rationale = String.format(
                "Cold-start %s (CTL baseline capado %.0f, rampa %.2f, contencao %.1f); %s",
                stage, ctlUsado, rampa, fatorContencao, decisao.motivo());
        return new WeeklyLoadTarget(target, min, max, rationale);
    }

    private double rampaPorEstagio(CalibrationStage stage) {
        return switch (stage) {
            case OBSERVATION -> RAMPA_OBSERVATION;
            case CALIBRATION -> RAMPA_CALIBRATION;
            case STABILIZATION -> RAMPA_STABILIZATION;
        };
    }

    private boolean isFaseDeReducao(TrainingPhase phase) {
        return phase == TrainingPhase.RECOVERY || phase == TrainingPhase.POST_RACE;
    }

    private boolean isFaseDeContencao(TrainingPhase phase) {
        return phase == TrainingPhase.TAPER
                || phase == TrainingPhase.RACE_WEEK
                || phase == TrainingPhase.RECOVERY
                || phase == TrainingPhase.POST_RACE
                || phase == TrainingPhase.RETURN_TO_TRAINING;
    }
}
