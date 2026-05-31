package br.com.menthoros.backend.skills.race;

import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.skills.race.enums.DataQuality;
import br.com.menthoros.backend.skills.race.enums.ProjectionConfidence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaceRegressionCalculatorTest {

    private PaceRegressionCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new PaceRegressionCalculator();
    }

    // sc_001: atleta com 12 semanas de treino consistente → HIGH confidence
    @Test
    void calculate_twelveWeeksConsistentWithHr_returnsHighConfidence() {
        List<WorkoutSummary> workouts = buildConsistentWorkouts(12, 320, 155, TipoTreino.TEMPO_RUN);
        TrainingHistory history = new TrainingHistory(workouts, DataQuality.FULL, 12);

        RegressionResult result = calculator.calculate(history, 190, 4);

        assertThat(result.confidenceLayer1()).isEqualTo(ProjectionConfidence.HIGH);
        assertThat(result.sessionsUsed()).isGreaterThanOrEqualTo(6);
        assertThat(result.fallbackUsed()).isFalse();
        assertThat(result.rSquared()).isGreaterThanOrEqualTo(0.70);
        assertThat(result.projectedPaceAtRaceWeek()).isPositive();
    }

    // sc_002: atleta com 3 semanas (< 6 sessões) → LOW confidence, fallback
    @Test
    void calculate_threeWeeksInsufficient_returnsLowConfidenceWithFallback() {
        List<WorkoutSummary> workouts = buildConsistentWorkouts(3, 330, 160, TipoTreino.TEMPO_RUN);
        TrainingHistory history = new TrainingHistory(workouts, DataQuality.PARTIAL, 3);

        RegressionResult result = calculator.calculate(history, 185, 6);

        assertThat(result.confidenceLayer1()).isEqualTo(ProjectionConfidence.LOW);
        assertThat(result.fallbackUsed()).isTrue();
        assertThat(result.sessionsUsed()).isLessThan(6);
        assertThat(result.projectedPaceAtRaceWeek()).isPositive();
    }

    // sc_003: sem dados de FC (maxHr=null) → LOW, key_assumption registrada
    @Test
    void calculate_noHrData_returnsLowWithHrAssumption() {
        List<WorkoutSummary> workouts = buildWorkoutsWithoutHr(10, 340, TipoTreino.LONGO);
        TrainingHistory history = new TrainingHistory(workouts, DataQuality.FULL, 10);

        RegressionResult result = calculator.calculate(history, null, 4);

        assertThat(result.confidenceLayer1()).isEqualTo(ProjectionConfidence.LOW);
        assertThat(result.hrMissingAssumption()).isNotNull().isNotBlank();
        assertThat(result.projectedPaceAtRaceWeek()).isPositive();
    }

    // sc_004: maxHr presente mas todos workouts sem avgHr → LOW, key_assumption registrada
    @Test
    void calculate_maxHrPresentButWorkoutsLackAvgHr_returnsLowWithAssumption() {
        List<WorkoutSummary> workouts = buildWorkoutsWithoutHr(8, 330, TipoTreino.TEMPO_RUN);
        TrainingHistory history = new TrainingHistory(workouts, DataQuality.FULL, 8);

        RegressionResult result = calculator.calculate(history, 190, 4);

        assertThat(result.confidenceLayer1()).isEqualTo(ProjectionConfidence.LOW);
        assertThat(result.hrMissingAssumption()).isNotNull();
    }

    // sc_005: data_quality = SPARSE → LOW obrigatório, fallback com último pace disponível
    @Test
    void calculate_sparseDataQuality_returnsLowFallbackWithLastPace() {
        List<WorkoutSummary> workouts = buildConsistentWorkouts(4, 350, 165, TipoTreino.LONGO);
        TrainingHistory history = new TrainingHistory(workouts, DataQuality.SPARSE, 4);

        RegressionResult result = calculator.calculate(history, 190, 8);

        assertThat(result.confidenceLayer1()).isEqualTo(ProjectionConfidence.LOW);
        assertThat(result.fallbackUsed()).isTrue();
        assertThat(result.rSquared()).isNull();
        assertThat(result.slope()).isNull();
        // 4 workouts com pace 350,348,346,344 → último pace = 344
        assertThat(result.projectedPaceAtRaceWeek()).isEqualTo(344.0, org.assertj.core.data.Offset.offset(1.0));
    }

    // sc_006: null trainingHistory → IllegalArgumentException
    @Test
    void calculate_nullTrainingHistory_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> calculator.calculate(null, 190, 4))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TrainingHistory cannot be null");
    }

    // sc_007: treinos INTERVALADO e REGENERATIVO não são contados (apenas TEMPO_RUN e LONGO)
    @Test
    void calculate_nonQualifyingWorkoutTypes_notCountedInRegression() {
        List<WorkoutSummary> workouts = buildConsistentWorkouts(3, 300, 170, TipoTreino.TEMPO_RUN);
        workouts.addAll(buildConsistentWorkouts(8, 280, 130, TipoTreino.REGENERATIVO));
        TrainingHistory history = new TrainingHistory(workouts, DataQuality.FULL, 11);

        RegressionResult result = calculator.calculate(history, 190, 4);

        // Apenas 3 sessões TEMPO_RUN qualificam → fallback
        assertThat(result.sessionsUsed()).isLessThan(6);
        assertThat(result.fallbackUsed()).isTrue();
    }

    // -------------------- helpers --------------------

    private List<WorkoutSummary> buildConsistentWorkouts(int weeks, int basePace, int baseHr, TipoTreino type) {
        List<WorkoutSummary> list = new ArrayList<>();
        LocalDate start = LocalDate.now().minusWeeks(weeks);
        for (int i = 0; i < weeks; i++) {
            // pace melhora levemente a cada semana (-2 seg/km) para garantir R² alto
            int pace = basePace - (i * 2);
            list.add(new WorkoutSummary(start.plusWeeks(i), type, 10000, 3600, pace, baseHr, null, null));
        }
        return list;
    }

    private List<WorkoutSummary> buildWorkoutsWithoutHr(int weeks, int basePace, TipoTreino type) {
        List<WorkoutSummary> list = new ArrayList<>();
        LocalDate start = LocalDate.now().minusWeeks(weeks);
        for (int i = 0; i < weeks; i++) {
            int pace = basePace - (i * 2);
            list.add(new WorkoutSummary(start.plusWeeks(i), type, 10000, 3600, pace, null, null, null));
        }
        return list;
    }
}
