package br.com.menthoros.backend.dto.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Withers de {@link EtapaTreinoLlmDto} (pipeline-normalizacao-treino, seção 1): cada um muda só o
 * seu campo e preserva os outros 7. Fixture com os 8 campos distintos e não-nulos: uma troca de
 * posição no construtor canônico ({@code duracaoMin}/{@code distanciaKm}, {@code ordem}/
 * {@code repeticoes} têm tipos compatíveis dois a dois) falha aqui.
 */
@DisplayName("EtapaTreinoLlmDto — withers")
class EtapaTreinoLlmDtoTest {

    private static final EtapaTreinoLlmDto ORIGINAL =
            new EtapaTreinoLlmDto(3, "INTERVALADO", "Tiro 1 - Z5", 4, 1.0, "160-170 bpm", 1, "4:00-4:15/km");

    @Nested
    @DisplayName("comOrdem")
    class ComOrdem {
        @Test
        @DisplayName("troca só ordem (renumeração de reordenarEtapas/reparador)")
        void preservaOsDemais() {
            assertThat(ORIGINAL.comOrdem(7)).isEqualTo(
                    new EtapaTreinoLlmDto(7, "INTERVALADO", "Tiro 1 - Z5", 4, 1.0, "160-170 bpm", 1, "4:00-4:15/km"));
        }
    }

    @Nested
    @DisplayName("comDuracao")
    class ComDuracao {
        @Test
        @DisplayName("troca só duracaoMin (recalculo do IA-05)")
        void preservaOsDemais() {
            assertThat(ORIGINAL.comDuracao(11)).isEqualTo(
                    new EtapaTreinoLlmDto(3, "INTERVALADO", "Tiro 1 - Z5", 11, 1.0, "160-170 bpm", 1, "4:00-4:15/km"));
        }
    }

    @Nested
    @DisplayName("comDistancia")
    class ComDistancia {
        @Test
        @DisplayName("troca só distanciaKm (clamp/distribuição)")
        void preservaOsDemais() {
            assertThat(ORIGINAL.comDistancia(0.9)).isEqualTo(
                    new EtapaTreinoLlmDto(3, "INTERVALADO", "Tiro 1 - Z5", 4, 0.9, "160-170 bpm", 1, "4:00-4:15/km"));
        }
    }

    @Nested
    @DisplayName("comFc")
    class ComFc {
        @Test
        @DisplayName("troca só fcAlvoEtapa (correção de zona)")
        void preservaOsDemais() {
            assertThat(ORIGINAL.comFc("150-160 bpm")).isEqualTo(
                    new EtapaTreinoLlmDto(3, "INTERVALADO", "Tiro 1 - Z5", 4, 1.0, "150-160 bpm", 1, "4:00-4:15/km"));
        }
    }

    @Test
    @DisplayName("o original não muda")
    void originalIntacto() {
        ORIGINAL.comOrdem(0).comDuracao(0).comDistancia(0.0).comFc("x");
        assertThat(ORIGINAL).isEqualTo(
                new EtapaTreinoLlmDto(3, "INTERVALADO", "Tiro 1 - Z5", 4, 1.0, "160-170 bpm", 1, "4:00-4:15/km"));
    }
}
