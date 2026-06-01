package br.com.menthoros.backend.skills.race;

import br.com.menthoros.backend.skills.race.enums.CtlTrend;
import br.com.menthoros.backend.skills.race.enums.GapAssessment;
import br.com.menthoros.backend.skills.race.enums.ProjectionConfidence;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Monta o {@link RaceProjectionOutput} completo a partir dos resultados das 3 camadas.
 *
 * Responsabilidades:
 * - Calcular time_range (±3%) para cada distância
 * - Determinar pr_potential comparando com melhor prova histórica
 * - Calcular CTLForecast
 * - Calcular GoalGapAnalysis quando coach_goal_override presente
 * - Montar o Map<Integer, RaceProjection> final
 *
 * Idempotent: YES — leitura pura, sem side effects.
 * Side Effects: NONE
 * Tenant-aware: NO
 */
@Component
public class RaceProjectionAssembler {

    private static final double OPTIMISTIC_FACTOR  = 0.97;
    private static final double CONSERVATIVE_FACTOR = 1.03;

    // Thresholds D8 do design.md
    private static final double ON_TRACK_THRESHOLD  = 0.02;
    private static final double REACHABLE_THRESHOLD = 0.05;
    private static final double STRETCH_THRESHOLD   = 0.10;

    /**
     * Monta o RaceProjectionOutput completo.
     *
     * @param adjustmentResult  resultado das 3 camadas (tempos ajustados por distância)
     * @param riegelResult      resultado da Camada 2 (calibração, âncora)
     * @param regressionResult  resultado da Camada 1 (confiança, R²)
     * @param loadProjection    projeção de carga (CTL, TSB, semanas)
     * @param pastRaces         provas anteriores para cálculo de PR potential
     * @param coachGoalOverride meta do coach (nullable)
     * @param narrative         saída textual do LLM
     * @param overallConfidence confiança combinada calculada pelo ConfidenceCalculator
     * @param weeksToRace       semanas até o dia da prova
     * @return output completo pronto para persistência e resposta REST
     */
    public RaceProjectionOutput assemble(
            AdjustmentResult adjustmentResult,
            RiegelResult riegelResult,
            RegressionResult regressionResult,
            LoadProjection loadProjection,
            List<PastRace> pastRaces,
            CoachGoalOverride coachGoalOverride,
            RaceProjectionNarrativeGenerator.NarrativeOutput narrative,
            ProjectionConfidence overallConfidence,
            int weeksToRace) {

        Map<Integer, RaceProjection> projections = buildProjections(
                adjustmentResult.adjustedTimes(), pastRaces, overallConfidence);

        CTLForecast ctlForecast = buildCtlForecast(loadProjection, weeksToRace);

        GoalGapAnalysis goalGapAnalysis = coachGoalOverride != null
                ? buildGoalGapAnalysis(coachGoalOverride, projections)
                : null;

        return new RaceProjectionOutput(
                projections,
                narrative.progressionNarrative(),
                ctlForecast,
                narrative.keyAssumptions(),
                narrative.coachNote(),
                goalGapAnalysis
        );
    }

    // --- time range + PR potential ---

    private Map<Integer, RaceProjection> buildProjections(
            Map<Integer, Long> adjustedTimes,
            List<PastRace> pastRaces,
            ProjectionConfidence confidence) {

        Map<Integer, RaceProjection> result = new HashMap<>();
        for (Map.Entry<Integer, Long> entry : adjustedTimes.entrySet()) {
            int distanceM = entry.getKey();
            long projectedSec = entry.getValue();

            long optimistic    = Math.round(projectedSec * OPTIMISTIC_FACTOR);
            long conservative  = Math.round(projectedSec * CONSERVATIVE_FACTOR);
            int  paceSec       = (int) Math.round((double) projectedSec / (distanceM / 1000.0));
            boolean prPotential = isPrPotential(distanceM, projectedSec, pastRaces);

            result.put(distanceM, new RaceProjection(
                    distanceM,
                    projectedSec,
                    paceSec,
                    optimistic,
                    conservative,
                    confidence,
                    prPotential
            ));
        }
        return result;
    }

    private boolean isPrPotential(int distanceM, long projectedSec, List<PastRace> pastRaces) {
        if (pastRaces == null || pastRaces.isEmpty()) return false;
        Optional<Long> bestTime = pastRaces.stream()
                .filter(r -> r.distanceM().equals(distanceM))
                .map(PastRace::finishTimeSeconds)
                .min(Long::compareTo);
        return bestTime.isPresent() && projectedSec < bestTime.get();
    }

    // --- CTLForecast ---

    private CTLForecast buildCtlForecast(LoadProjection load, int weeksToRace) {
        double currentCtl   = load.currentCtl() != null ? load.currentCtl() : 0.0;
        double projectedCtl = load.projectedCtlOnRaceDay() != null ? load.projectedCtlOnRaceDay() : currentCtl;

        CtlTrend trend;
        Integer weeksToPeak = null;
        if (weeksToRace > 0) {
            double weeklyDelta = (projectedCtl - currentCtl) / weeksToRace;
            if (weeklyDelta > 0) {
                trend = CtlTrend.BUILDING;
                weeksToPeak = (int) Math.round((projectedCtl - currentCtl) / Math.max(weeklyDelta, 0.01));
            } else if (weeklyDelta < -1.0) {
                trend = CtlTrend.DECLINING;
            } else {
                trend = CtlTrend.STABLE;
            }
        } else {
            trend = CtlTrend.STABLE;
        }

        return new CTLForecast(currentCtl, projectedCtl, trend, weeksToPeak);
    }

    // --- GoalGapAnalysis ---

    private GoalGapAnalysis buildGoalGapAnalysis(CoachGoalOverride override,
                                                  Map<Integer, RaceProjection> projections) {
        RaceProjection projection = projections.get(override.targetDistanceM());
        if (projection == null) return null;

        long goalSec      = override.goalTimeSeconds();
        long projectedSec = projection.projectedTimeSeconds();
        long gapSec       = projectedSec - goalSec;
        double gapPct     = (double) gapSec / goalSec;

        GapAssessment assessment = classifyGap(gapPct);

        String coachNoteGap = String.format(
                "Projeção atual: %s. Meta: %s (gap %.1f%% — %s).",
                formatTime(projectedSec),
                formatTime(goalSec),
                Math.abs(gapPct) * 100,
                assessment.name()
        );

        return new GoalGapAnalysis(goalSec, projectedSec, gapSec, gapPct, assessment, coachNoteGap);
    }

    private GapAssessment classifyGap(double gapPct) {
        double abs = Math.abs(gapPct);
        if (abs <= ON_TRACK_THRESHOLD)  return GapAssessment.ON_TRACK;
        if (abs <= REACHABLE_THRESHOLD) return GapAssessment.REACHABLE;
        if (abs <= STRETCH_THRESHOLD)   return GapAssessment.STRETCH;
        return GapAssessment.UNLIKELY;
    }

    private String formatTime(long totalSeconds) {
        long h = totalSeconds / 3600;
        long m = (totalSeconds % 3600) / 60;
        long s = totalSeconds % 60;
        return h > 0
                ? String.format("%dh%02dmin", h, m)
                : String.format("%dmin%02ds", m, s);
    }
}
