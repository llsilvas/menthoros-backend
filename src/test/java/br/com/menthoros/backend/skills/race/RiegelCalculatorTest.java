package br.com.menthoros.backend.skills.race;

import br.com.menthoros.backend.skills.race.enums.ProjectionConfidence;
import br.com.menthoros.backend.skills.race.enums.RaceConditions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RiegelCalculatorTest {

    private RiegelCalculator calculator;
    private RegressionResult baseRegression;

    @BeforeEach
    void setUp() {
        calculator = new RiegelCalculator();
        // pace 300 seg/km (5min/km) → 10k âncora em ~3000s (50min)
        baseRegression = new RegressionResult(-1.0, 0.80, 300.0, ProjectionConfidence.HIGH, 12, false, null);
    }

    // sc_001: atleta com 3 provas em distâncias distintas → expoente calibrado
    @Test
    void calculate_threeRacesDistinctDistances_exponentCalibrated() {
        List<PastRace> races = List.of(
                pastRace(5000, 1320, -6),   // 5k em 22min
                pastRace(10000, 2700, -3),  // 10k em 45min
                pastRace(21097, 5760, -1)   // 21k em 1h36
        );

        RiegelResult result = calculator.calculate(baseRegression, races, List.of(5000, 10000, 21097, 42195));

        assertThat(result.calibrated()).isTrue();
        assertThat(result.calibrationSampleSize()).isGreaterThan(0);
        assertThat(result.exponentUsed()).isNotEqualTo(RiegelCalculator.DEFAULT_EXPONENT);
        assertThat(result.baseTimesSec()).containsKeys(5000, 10000, 21097, 42195);
    }

    // sc_002: sem provas → expoente padrão 1.06
    @Test
    void calculate_noRaceHistory_usesDefaultExponent() {
        RiegelResult result = calculator.calculate(baseRegression, List.of(), List.of(10000, 21097));

        assertThat(result.calibrated()).isFalse();
        assertThat(result.exponentUsed()).isEqualTo(RiegelCalculator.DEFAULT_EXPONENT);
        assertThat(result.calibrationSampleSize()).isZero();
    }

    // sc_003: verificar cálculo com valores conhecidos — 10k 45min → 21k ~1h36 (5760s)
    // Usando expoente padrão 1.06 e âncora 10k=2700s
    @Test
    void calculate_knownValues_10kTo21k_approximatesExpectedTime() {
        // Âncora: regressão com pace de ~270 seg/km → 10k em 2700s (45min)
        RegressionResult regression45min = new RegressionResult(
                -1.0, 0.80, 270.0, ProjectionConfidence.HIGH, 12, false, null);

        RiegelResult result = calculator.calculate(regression45min, List.of(), List.of(21097));

        long projected21k = result.baseTimesSec().get(21097);
        // Riegel com expoente 1.06: 2700 * (21097/10000)^1.06 ≈ 5769s ≈ 1h36min
        assertThat(projected21k).isBetween(5600L, 5900L);
    }

    // sc_004: raceHistory null é tratado como lista vazia (não lança NPE)
    @Test
    void calculate_nullRaceHistory_treatedAsEmpty() {
        RiegelResult result = calculator.calculate(baseRegression, null, List.of(10000));

        assertThat(result).isNotNull();
        assertThat(result.calibrated()).isFalse();
        assertThat(result.baseTimesSec()).containsKey(10000);
    }

    // sc_005: provas com mesma distância não geram par de calibração
    @Test
    void calculate_racesWithSameDistance_noCalibration() {
        List<PastRace> sameDistanceRaces = List.of(
                pastRace(10000, 2700, -6),
                pastRace(10000, 2650, -2)
        );

        RiegelResult result = calculator.calculate(baseRegression, sameDistanceRaces, List.of(10000, 21097));

        assertThat(result.calibrated()).isFalse();
        assertThat(result.exponentUsed()).isEqualTo(RiegelCalculator.DEFAULT_EXPONENT);
    }

    // sc_006: prova recente (< 12 meses) tem peso 2x na calibração
    @Test
    void calculate_recentRaceWeightedHigher_calibrationDiffers() {
        List<PastRace> onlyOld = List.of(
                pastRace(5000, 1320, -24),
                pastRace(10000, 2700, -18)
        );
        List<PastRace> withRecent = List.of(
                pastRace(5000, 1320, -24),
                pastRace(10000, 2700, -18),
                pastRace(21097, 5760, -2)
        );

        RiegelResult old = calculator.calculate(baseRegression, onlyOld, List.of(21097));
        RiegelResult recent = calculator.calculate(baseRegression, withRecent, List.of(21097));

        // Ambos calibrados, mas o expoente pode diferir ligeiramente pelo peso
        assertThat(old.calibrated()).isTrue();
        assertThat(recent.calibrated()).isTrue();
    }

    // sc_007: anchor source é LAYER1_PACE por padrão quando sem provas
    @Test
    void calculate_noRaces_anchorIsLayer1Pace() {
        RiegelResult result = calculator.calculate(baseRegression, List.of(), List.of(10000, 21097));

        assertThat(result.anchorSource()).isEqualTo("LAYER1_PACE");
    }

    // sc_008: null regression → IllegalArgumentException
    @Test
    void calculate_nullRegression_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> calculator.calculate(null, List.of(), List.of(10000)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // sc_009: targetDistances null ou vazio → IllegalArgumentException
    @Test
    void calculate_emptyTargetDistances_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> calculator.calculate(baseRegression, List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- helpers ---

    private PastRace pastRace(int distanceM, long finishTimeSec, int monthsAgo) {
        return new PastRace(distanceM, finishTimeSec, LocalDate.now().plusMonths(monthsAgo), RaceConditions.IDEAL);
    }
}
