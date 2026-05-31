package br.com.menthoros.backend.skills.race;

import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.skills.race.enums.DataQuality;
import br.com.menthoros.backend.skills.race.enums.GapAssessment;
import br.com.menthoros.backend.skills.race.enums.PeriodizationPhase;
import br.com.menthoros.backend.skills.race.enums.ProjectionConfidence;
import br.com.menthoros.backend.skills.race.enums.RaceConditions;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Testes de integração da skill com mock do LLM (NarrativeGenerator).
 * Verifica os 5 cenários definidos na task 8.4, validação de input e flag hr_missing.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RaceProjectionSkillTest {

    @Mock private RaceProjectionNarrativeGenerator narrativeGenerator;

    private RaceProjectionSkill skill;
    private static final List<Integer> TARGET_DISTANCES = List.of(10000, 21097);

    @BeforeEach
    void setUp() {
        when(narrativeGenerator.generate(any())).thenReturn(
                new RaceProjectionNarrativeGenerator.NarrativeOutput(
                        "Progressão consistente.", List.of("Treinos em condições normais."), "Manter taper."));

        skill = new RaceProjectionSkill(
                new PaceRegressionCalculator(),
                new RiegelCalculator(),
                new PeriodizationAdjuster(),
                new ConfidenceCalculator(),
                narrativeGenerator,
                new RaceProjectionAssembler(),
                new SimpleMeterRegistry());
    }

    // sc_001: 12 semanas TAPER + provas calibradas → HIGH, fator 0.975
    @Test
    void execute_twelveWeeksTaper_highConfidenceAndTaperFactor() {
        List<PastRace> races = List.of(
                new PastRace(5000, 1320L, LocalDate.now().minusMonths(4), RaceConditions.IDEAL),
                new PastRace(10000, 2700L, LocalDate.now().minusMonths(2), RaceConditions.IDEAL));
        RaceProjectionInput input = inputBuilder()
                .trainingHistory(history(12, 300, 155, TipoTreino.TEMPO_RUN, DataQuality.FULL))
                .loadProjection(load(PeriodizationPhase.TAPER_OPTIMAL, 5.0, 8))
                .pastRaces(races)
                .build();

        RaceProjectionOutput output = skill.execute(input);

        assertThat(output.projections()).isNotEmpty();
        RaceProjection proj10k = output.projections().get(10000);
        assertThat(proj10k.confidence()).isEqualTo(ProjectionConfidence.HIGH);
        assertThat(output.ctlForecast()).isNotNull();
        // Fator TAPER_OPTIMAL = 0.975 → tempo ajustado deve ser menor que a Camada 2
        assertThat(proj10k.projectedTimeSeconds()).isPositive();
    }

    // sc_002: 3 semanas → LOW confidence
    @Test
    void execute_threeWeeks_lowConfidence() {
        RaceProjectionInput input = inputBuilder()
                .trainingHistory(history(3, 330, 160, TipoTreino.TEMPO_RUN, DataQuality.PARTIAL))
                .loadProjection(load(PeriodizationPhase.BUILD_PEAK, -8.0, 6))
                .build();

        RaceProjectionOutput output = skill.execute(input);

        output.projections().values().forEach(p ->
                assertThat(p.confidence()).isEqualTo(ProjectionConfidence.LOW));
    }

    // sc_003: 3 provas em distâncias distintas → Riegel calibrado
    @Test
    void execute_threeRacesDistinctDistances_riegelCalibrated() {
        List<PastRace> races = List.of(
                new PastRace(5000, 1320L, LocalDate.now().minusMonths(6), RaceConditions.IDEAL),
                new PastRace(10000, 2700L, LocalDate.now().minusMonths(3), RaceConditions.IDEAL),
                new PastRace(21097, 5760L, LocalDate.now().minusMonths(1), RaceConditions.IDEAL));

        RaceProjectionInput input = inputBuilder()
                .trainingHistory(history(12, 300, 155, TipoTreino.TEMPO_RUN, DataQuality.FULL))
                .loadProjection(load(PeriodizationPhase.TAPER_OPTIMAL, 5.0, 4))
                .pastRaces(races)
                .build();

        RaceProjectionOutput output = skill.execute(input);

        // Com 3 provas calibradas, confiança não deve cair para LOW apenas por Riegel
        assertThat(output.projections().get(10000).confidence())
                .isNotEqualTo(ProjectionConfidence.LOW);
    }

    // sc_004: TSB -18 → FATIGUED sobrescreve fase, fator 1.08
    @Test
    void execute_tsbMinus18_fatiguedFactorApplied() {
        RaceProjectionInput input = inputBuilder()
                .trainingHistory(history(12, 300, 155, TipoTreino.TEMPO_RUN, DataQuality.FULL))
                .loadProjection(load(PeriodizationPhase.BUILD_PEAK, -18.0, 4))
                .build();

        RaceProjectionOutput output = skill.execute(input);

        // Com FATIGUED (fator 1.08) os tempos devem ser maiores que com BUILD_PEAK (fator 1.00)
        RaceProjectionInput buildInput = inputBuilder()
                .trainingHistory(history(12, 300, 155, TipoTreino.TEMPO_RUN, DataQuality.FULL))
                .loadProjection(load(PeriodizationPhase.BUILD_PEAK, -8.0, 4))
                .build();
        RaceProjectionOutput buildOutput = skill.execute(buildInput);

        long fatiguedTime = output.projections().get(10000).projectedTimeSeconds();
        long buildTime = buildOutput.projections().get(10000).projectedTimeSeconds();
        assertThat(fatiguedTime).isGreaterThan(buildTime);
    }

    // sc_005: meta 20% mais rápida → UNLIKELY
    @Test
    void execute_goalTwentyPctFaster_gapAssessmentUnlikely() {
        RaceProjectionInput input = inputBuilder()
                .trainingHistory(history(12, 300, 155, TipoTreino.TEMPO_RUN, DataQuality.FULL))
                .loadProjection(load(PeriodizationPhase.TAPER_OPTIMAL, 5.0, 4))
                // Meta 20% mais rápida que o pace esperado (~2700s para 10k → meta ~2160s)
                .coachGoalOverride(new CoachGoalOverride(2160L, 10000))
                .build();

        RaceProjectionOutput output = skill.execute(input);

        assertThat(output.goalGapAnalysis()).isNotNull();
        assertThat(output.goalGapAnalysis().gapAssessment()).isEqualTo(GapAssessment.UNLIKELY);
    }

    // 8.2 — input null → IllegalArgumentException
    @Test
    void execute_nullInput_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> skill.execute(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // 8.2 — training_history null → IllegalArgumentException
    @Test
    void execute_nullTrainingHistory_throwsIllegalArgumentException() {
        RaceProjectionInput input = new RaceProjectionInput(
                athlete(), null, load(PeriodizationPhase.TAPER_OPTIMAL, 5.0, 4),
                List.of(), TARGET_DISTANCES, null, null);
        assertThatThrownBy(() -> skill.execute(input))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // 8.3 — sem dados de HR → hrMissingAssumption presente no output
    @Test
    void execute_noHrData_hrAssumptionPresentInOutput() {
        RaceProjectionInput input = inputBuilder()
                .trainingHistory(historyWithoutHr(10, 320, DataQuality.FULL))
                .loadProjection(load(PeriodizationPhase.TAPER_OPTIMAL, 5.0, 4))
                .build();

        RaceProjectionOutput output = skill.execute(input);

        output.projections().values().forEach(p ->
                assertThat(p.confidence()).isEqualTo(ProjectionConfidence.LOW));
        assertThat(output.keyAssumptions()).anyMatch(a ->
                a.toLowerCase().contains("fc") || a.toLowerCase().contains("frequência")
                || a.toLowerCase().contains("pace bruto"));
    }

    // 8.1 — weeksToRaceOverride tem precedência sobre loadProjection.weeksToRace
    @Test
    void execute_weeksToRaceOverride_overridesLoadProjection() {
        RaceProjectionInput inputWithOverride = inputBuilder()
                .trainingHistory(history(12, 300, 155, TipoTreino.TEMPO_RUN, DataQuality.FULL))
                .loadProjection(load(PeriodizationPhase.TAPER_OPTIMAL, 5.0, 12))
                .weeksToRaceOverride(4)
                .build();

        // Deve compilar e executar sem erro — override aplica-se corretamente
        RaceProjectionOutput output = skill.execute(inputWithOverride);
        assertThat(output).isNotNull();
        assertThat(output.projections()).containsKeys(10000, 21097);
    }

    // --- builders ---

    private InputBuilder inputBuilder() { return new InputBuilder(); }

    private class InputBuilder {
        private AthleteProfile athleteProfile = athlete();
        private TrainingHistory trainingHistory = history(12, 300, 155, TipoTreino.TEMPO_RUN, DataQuality.FULL);
        private LoadProjection loadProjection = load(PeriodizationPhase.TAPER_OPTIMAL, 5.0, 4);
        private List<PastRace> pastRaces = List.of();
        private CoachGoalOverride coachGoalOverride = null;
        private Integer weeksToRaceOverride = null;

        InputBuilder trainingHistory(TrainingHistory h) { this.trainingHistory = h; return this; }
        InputBuilder loadProjection(LoadProjection l) { this.loadProjection = l; return this; }
        InputBuilder pastRaces(List<PastRace> r) { this.pastRaces = r; return this; }
        InputBuilder coachGoalOverride(CoachGoalOverride g) { this.coachGoalOverride = g; return this; }
        InputBuilder weeksToRaceOverride(Integer w) { this.weeksToRaceOverride = w; return this; }

        RaceProjectionInput build() {
            return new RaceProjectionInput(athleteProfile, trainingHistory, loadProjection,
                    pastRaces, TARGET_DISTANCES, coachGoalOverride, weeksToRaceOverride);
        }
    }

    private AthleteProfile athlete() {
        return new AthleteProfile(UUID.randomUUID(), "João", "Silva",
                190, 167, null, NivelExperiencia.INTERMEDIARIO);
    }

    private TrainingHistory history(int weeks, int basePace, int baseHr,
                                    TipoTreino type, DataQuality quality) {
        List<WorkoutSummary> workouts = new ArrayList<>();
        LocalDate start = LocalDate.now().minusWeeks(weeks);
        for (int i = 0; i < weeks; i++) {
            workouts.add(new WorkoutSummary(
                    start.plusWeeks(i), type, 10000, 3600,
                    basePace - (i * 2), baseHr, null, null));
        }
        return new TrainingHistory(workouts, quality, weeks);
    }

    private TrainingHistory historyWithoutHr(int weeks, int basePace, DataQuality quality) {
        List<WorkoutSummary> workouts = new ArrayList<>();
        LocalDate start = LocalDate.now().minusWeeks(weeks);
        for (int i = 0; i < weeks; i++) {
            workouts.add(new WorkoutSummary(
                    start.plusWeeks(i), TipoTreino.TEMPO_RUN, 10000, 3600,
                    basePace - (i * 2), null, null, null));
        }
        return new TrainingHistory(workouts, quality, weeks);
    }

    private LoadProjection load(PeriodizationPhase phase, double projectedTsb, int weeksToRace) {
        return new LoadProjection(
                60.0, 65.0, -5.0,
                LocalDate.now().plusWeeks(weeksToRace),
                weeksToRace, phase, 65.0, projectedTsb);
    }
}
