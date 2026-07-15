package br.com.menthoros.backend.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FonteDadosTest {

    @Test
    @DisplayName("INTERVALS_ICU existe e faz round-trip por valueOf")
    void intervalsIcuRoundTrip() {
        FonteDados fonte = FonteDados.valueOf("INTERVALS_ICU");
        assertThat(fonte.getValue()).isEqualTo("INTERVALS_ICU");
        assertThat(fonte.getLabel()).isEqualTo("intervals.icu");
    }
}
