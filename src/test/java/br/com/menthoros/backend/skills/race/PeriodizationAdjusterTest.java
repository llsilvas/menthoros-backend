package br.com.menthoros.backend.skills.race;

import br.com.menthoros.backend.skills.race.enums.PeriodizationPhase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;

class PeriodizationAdjusterTest {

    private PeriodizationAdjuster adjuster;
    private static final Map<Integer, Long> BASE_TIMES = Map.of(10000, 2700L, 21097, 5760L);

    @BeforeEach
    void setUp() {
        adjuster = new PeriodizationAdjuster();
    }

    // --- fator correto para cada cenário (borda inferior, centro, borda superior de TSB) ---

    @ParameterizedTest(name = "phase={0} tsb={1} → factor={2}")
    @CsvSource({
            "TAPER_OPTIMAL,    -5.0,  0.975",
            "TAPER_OPTIMAL,     2.5,  0.975",
            "TAPER_OPTIMAL,    10.0,  0.975",
            "BUILD_PEAK,       -15.0, 1.000",
            "BUILD_PEAK,       -10.0, 1.000",
            "BUILD_PEAK,        -5.0, 1.000",
            "ESPECIFICO_FRESH,  -5.0, 0.985",
            "ESPECIFICO_FRESH,   0.0, 0.985",
            "ESPECIFICO_FRESH,   5.0, 0.985",
            "BASE_CONSERVATIVE,  0.0, 1.050",
            "FATIGUED,         -20.0, 1.080",
            "OVERTAPERED,       20.0, 1.030"
    })
    void adjust_knownPhase_appliesCorrectFactor(PeriodizationPhase phase, double tsb, double expectedFactor) {
        AdjustmentResult result = adjuster.adjust(BASE_TIMES, phase, tsb);

        assertThat(result.adjustmentFactor()).isEqualTo(expectedFactor, offset(0.001));
        assertThat(result.adjustmentRationaleKey()).isEqualTo(phase.name());
    }

    // --- precedência: FATIGUED sobrescreve qualquer fase quando TSB < -15 ---

    @Test
    void adjust_tsbBelowFatiguedThreshold_overridesDeclaredPhase() {
        AdjustmentResult result = adjuster.adjust(BASE_TIMES, PeriodizationPhase.TAPER_OPTIMAL, -16.0);

        assertThat(result.adjustmentFactor()).isEqualTo(1.080, offset(0.001));
        assertThat(result.adjustmentRationaleKey()).isEqualTo("FATIGUED");
    }

    @Test
    void adjust_tsbExactlyFatiguedBoundary_notFatigued() {
        // TSB = -15 (não < -15) → fase declarada prevalece
        AdjustmentResult result = adjuster.adjust(BASE_TIMES, PeriodizationPhase.BUILD_PEAK, -15.0);

        assertThat(result.adjustmentRationaleKey()).isEqualTo("BUILD_PEAK");
        assertThat(result.adjustmentFactor()).isEqualTo(1.000, offset(0.001));
    }

    // --- precedência: OVERTAPERED sobrescreve qualquer fase quando TSB > +15 ---

    @Test
    void adjust_tsbAboveOvertaperedThreshold_overridesDeclaredPhase() {
        AdjustmentResult result = adjuster.adjust(BASE_TIMES, PeriodizationPhase.TAPER_OPTIMAL, 16.0);

        assertThat(result.adjustmentFactor()).isEqualTo(1.030, offset(0.001));
        assertThat(result.adjustmentRationaleKey()).isEqualTo("OVERTAPERED");
    }

    // --- fator aplicado corretamente nos tempos ---

    @Test
    void adjust_taperOptimal_timesReducedBy2Point5Percent() {
        AdjustmentResult result = adjuster.adjust(Map.of(10000, 3000L), PeriodizationPhase.TAPER_OPTIMAL, 5.0);

        assertThat(result.adjustedTimes().get(10000)).isEqualTo(Math.round(3000 * 0.975));
    }

    @Test
    void adjust_fatigued_timesIncreasedBy8Percent() {
        AdjustmentResult result = adjuster.adjust(Map.of(10000, 3000L), PeriodizationPhase.FATIGUED, -20.0);

        assertThat(result.adjustedTimes().get(10000)).isEqualTo(Math.round(3000 * 1.080));
    }

    // --- tsb null → fase declarada sem sobrescrita ---

    @Test
    void adjust_nullTsb_usesDeclaredPhase() {
        AdjustmentResult result = adjuster.adjust(BASE_TIMES, PeriodizationPhase.BASE_CONSERVATIVE, null);

        assertThat(result.adjustmentRationaleKey()).isEqualTo("BASE_CONSERVATIVE");
        assertThat(result.adjustmentFactor()).isEqualTo(1.050, offset(0.001));
    }

    // --- inputs inválidos ---

    @Test
    void adjust_nullBaseTimes_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> adjuster.adjust(null, PeriodizationPhase.TAPER_OPTIMAL, 5.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void adjust_nullPhase_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> adjuster.adjust(BASE_TIMES, null, 5.0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
