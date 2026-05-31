package br.com.menthoros.backend.skills.race;

import br.com.menthoros.backend.skills.race.enums.CtlTrend;
import br.com.menthoros.backend.skills.race.enums.GapAssessment;
import br.com.menthoros.backend.skills.race.enums.PeriodizationPhase;
import br.com.menthoros.backend.skills.race.enums.ProjectionConfidence;
import br.com.menthoros.backend.skills.race.enums.RaceConditions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class RaceProjectionAssemblerTest {

    private RaceProjectionAssembler assembler;

    private static final Map<Integer, Long> BASE_TIMES = Map.of(10000, 2700L, 21097, 5760L);
    private static final AdjustmentResult TAPER_ADJUSTMENT = new AdjustmentResult(0.975, "TAPER_OPTIMAL",
            Map.of(10000, Math.round(2700 * 0.975), 21097, Math.round(5760 * 0.975)));
    private static final RiegelResult RIEGEL_CALIBRATED = new RiegelResult(BASE_TIMES, 1.06, true, 3, "LAYER1_PACE");
    private static final RegressionResult REGRESSION_HIGH = new RegressionResult(-1.0, 0.82, 270.0,
            ProjectionConfidence.HIGH, 12, false, null);
    private static final RaceProjectionNarrativeGenerator.NarrativeOutput NARRATIVE =
            new RaceProjectionNarrativeGenerator.NarrativeOutput(
                    "Progressão consistente.", List.of("Premissa 1"), "Manter taper.");

    @BeforeEach
    void setUp() { assembler = new RaceProjectionAssembler(); }

    // --- time range ±3% ---

    @Test
    void assemble_timeRange_optimisticIs97PctAndConservativeIs103Pct() {
        RaceProjectionOutput output = assembler.assemble(
                TAPER_ADJUSTMENT, RIEGEL_CALIBRATED, REGRESSION_HIGH,
                loadProjection(60, 70, 8), List.of(),
                null, NARRATIVE, ProjectionConfidence.HIGH, 4);

        RaceProjection proj = output.projections().get(10000);
        long adjusted = Math.round(2700 * 0.975);
        assertThat(proj.timeRangeOptimisticSec()).isEqualTo(Math.round(adjusted * 0.97));
        assertThat(proj.timeRangeConservativeSec()).isEqualTo(Math.round(adjusted * 1.03));
    }

    // --- pr_potential ---

    @Test
    void assemble_projectedFasterThanPR_prPotentialTrue() {
        PastRace pr10k = new PastRace(10000, 2800L, LocalDate.now().minusMonths(6), RaceConditions.IDEAL);

        RaceProjectionOutput output = assembler.assemble(
                TAPER_ADJUSTMENT, RIEGEL_CALIBRATED, REGRESSION_HIGH,
                loadProjection(60, 70, 8), List.of(pr10k),
                null, NARRATIVE, ProjectionConfidence.HIGH, 4);

        // adjusted 10k = 2700 * 0.975 = 2633 < PR 2800 → true
        assertThat(output.projections().get(10000).prPotential()).isTrue();
    }

    @Test
    void assemble_projectedSlowerThanPR_prPotentialFalse() {
        PastRace pr10k = new PastRace(10000, 2500L, LocalDate.now().minusMonths(6), RaceConditions.IDEAL);

        RaceProjectionOutput output = assembler.assemble(
                TAPER_ADJUSTMENT, RIEGEL_CALIBRATED, REGRESSION_HIGH,
                loadProjection(60, 70, 8), List.of(pr10k),
                null, NARRATIVE, ProjectionConfidence.HIGH, 4);

        assertThat(output.projections().get(10000).prPotential()).isFalse();
    }

    // --- CTLForecast ---

    @Test
    void assemble_ctlBuilding_trendIsBuilding() {
        RaceProjectionOutput output = assembler.assemble(
                TAPER_ADJUSTMENT, RIEGEL_CALIBRATED, REGRESSION_HIGH,
                loadProjection(50, 70, 8), List.of(),
                null, NARRATIVE, ProjectionConfidence.HIGH, 8);

        assertThat(output.ctlForecast().ctlTrend()).isEqualTo(CtlTrend.BUILDING);
        assertThat(output.ctlForecast().currentCtl()).isEqualTo(50.0, offset(0.01));
        assertThat(output.ctlForecast().projectedCtlRaceDay()).isEqualTo(70.0, offset(0.01));
    }

    @Test
    void assemble_ctlDeclining_trendIsDeclining() {
        RaceProjectionOutput output = assembler.assemble(
                TAPER_ADJUSTMENT, RIEGEL_CALIBRATED, REGRESSION_HIGH,
                loadProjection(70, 55, 8), List.of(),
                null, NARRATIVE, ProjectionConfidence.HIGH, 8);

        assertThat(output.ctlForecast().ctlTrend()).isEqualTo(CtlTrend.DECLINING);
    }

    @Test
    void assemble_ctlStable_trendIsStable() {
        // weeklyDelta = (60 - 60) / 4 = 0 → não é > 0 (BUILDING) nem < -1 (DECLINING) → STABLE
        RaceProjectionOutput output = assembler.assemble(
                TAPER_ADJUSTMENT, RIEGEL_CALIBRATED, REGRESSION_HIGH,
                loadProjection(60, 60, 4), List.of(),
                null, NARRATIVE, ProjectionConfidence.HIGH, 4);

        assertThat(output.ctlForecast().ctlTrend()).isEqualTo(CtlTrend.STABLE);
    }

    // --- GoalGapAnalysis ---

    @Test
    void assemble_goalGapPresent_onTrackWhenWithin2Pct() {
        // adjusted 10k ≈ 2633s; meta 2650s (gap ~0.6%) → ON_TRACK
        CoachGoalOverride goal = new CoachGoalOverride(2650L, 10000);
        RaceProjectionOutput output = assembler.assemble(
                TAPER_ADJUSTMENT, RIEGEL_CALIBRATED, REGRESSION_HIGH,
                loadProjection(60, 70, 4), List.of(),
                goal, NARRATIVE, ProjectionConfidence.HIGH, 4);

        assertThat(output.goalGapAnalysis()).isNotNull();
        assertThat(output.goalGapAnalysis().gapAssessment()).isEqualTo(GapAssessment.ON_TRACK);
    }

    @Test
    void assemble_goalGapAbsent_nullGoalGapAnalysis() {
        RaceProjectionOutput output = assembler.assemble(
                TAPER_ADJUSTMENT, RIEGEL_CALIBRATED, REGRESSION_HIGH,
                loadProjection(60, 70, 4), List.of(),
                null, NARRATIVE, ProjectionConfidence.HIGH, 4);

        assertThat(output.goalGapAnalysis()).isNull();
    }

    @Test
    void assemble_goalGap20PctFaster_assessmentUnlikely() {
        // adjusted 10k ≈ 2633s; meta 2100s (gap ~25%) → UNLIKELY
        CoachGoalOverride goal = new CoachGoalOverride(2100L, 10000);
        RaceProjectionOutput output = assembler.assemble(
                TAPER_ADJUSTMENT, RIEGEL_CALIBRATED, REGRESSION_HIGH,
                loadProjection(60, 70, 4), List.of(),
                goal, NARRATIVE, ProjectionConfidence.HIGH, 4);

        assertThat(output.goalGapAnalysis().gapAssessment()).isEqualTo(GapAssessment.UNLIKELY);
    }

    @Test
    void assemble_narrativeAndAssumptionsPassedThrough() {
        RaceProjectionOutput output = assembler.assemble(
                TAPER_ADJUSTMENT, RIEGEL_CALIBRATED, REGRESSION_HIGH,
                loadProjection(60, 70, 4), List.of(),
                null, NARRATIVE, ProjectionConfidence.HIGH, 4);

        assertThat(output.progressionNarrative()).isEqualTo("Progressão consistente.");
        assertThat(output.keyAssumptions()).containsExactly("Premissa 1");
        assertThat(output.coachNote()).isEqualTo("Manter taper.");
    }

    // --- helpers ---

    private LoadProjection loadProjection(double currentCtl, double projectedCtl, int weeksToRace) {
        return new LoadProjection(
                currentCtl, 10.0, 5.0,
                LocalDate.now().plusWeeks(weeksToRace),
                weeksToRace,
                PeriodizationPhase.TAPER_OPTIMAL,
                projectedCtl, 5.0
        );
    }
}
