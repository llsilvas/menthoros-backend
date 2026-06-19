package br.com.menthoros.backend.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MotivoAtencao")
class MotivoAtencaoTest {

    @ParameterizedTest
    @EnumSource(MotivoAtencao.class)
    @DisplayName("todo motivo tem suggestedAction não-vazio e peso positivo")
    void contratoCompleto(MotivoAtencao motivo) {
        assertThat(motivo.getSuggestedAction()).isNotBlank();
        assertThat(motivo.getPeso()).isPositive();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("pesos de Severidade são estritamente ordenados CRITICA > ALTA > MEDIA")
    void severidadeOrdenada() {
        assertThat(Severidade.CRITICA.getPeso())
                .isGreaterThan(Severidade.ALTA.getPeso());
        assertThat(Severidade.ALTA.getPeso())
                .isGreaterThan(Severidade.MEDIA.getPeso());
    }
}
