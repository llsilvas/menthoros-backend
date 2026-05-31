package br.com.menthoros.backend.skills.race;

import br.com.menthoros.backend.skills.race.enums.ProjectionConfidence;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfidenceCalculatorTest {

    private ConfidenceCalculator calculator;

    @BeforeEach
    void setUp() { calculator = new ConfidenceCalculator(); }

    @ParameterizedTest(name = "regression={0} calibrated={1} → overall={2}")
    @CsvSource({
            "HIGH,   true,  HIGH",    // HIGH + calibrado → HIGH
            "HIGH,   false, MEDIUM",  // HIGH + não calibrado → MEDIUM (downgrade Riegel)
            "MEDIUM, true,  MEDIUM",  // MEDIUM + calibrado → MEDIUM
            "MEDIUM, false, LOW",     // MEDIUM + não calibrado → LOW
            "LOW,    true,  LOW",     // LOW + calibrado → LOW (mínimo é LOW)
            "LOW,    false, LOW"      // LOW + não calibrado → LOW
    })
    void calculate_combinationMatrix(ProjectionConfidence regression, boolean calibrated,
                                     ProjectionConfidence expected) {
        assertThat(calculator.calculate(regression, calibrated)).isEqualTo(expected);
    }

    @Test
    void calculate_nullConfidence_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> calculator.calculate(null, true))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
