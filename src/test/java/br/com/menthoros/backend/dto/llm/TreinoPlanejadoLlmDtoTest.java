package br.com.menthoros.backend.dto.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Withers de {@link TreinoPlanejadoLlmDto} (pipeline-normalizacao-treino, seção 1): cada um muda só
 * o seu campo e preserva os outros 13 — inclusive os três que o overload de 11 argumentos zeraria
 * ({@code descricao}, {@code zonaAlvo}, {@code provaId}). A fixture tem todos os 14 campos distintos
 * e não-nulos de propósito: uma troca de posição no construtor canônico falha aqui.
 */
@DisplayName("TreinoPlanejadoLlmDto — withers")
class TreinoPlanejadoLlmDtoTest {

    private static final UUID PROVA_ID = UUID.fromString("00000000-0000-0000-0000-00000000cafe");
    private static final List<EtapaTreinoLlmDto> ETAPAS = List.of(
            new EtapaTreinoLlmDto(1, "AQUECIMENTO", "aq", 10, 1.5, "120-136 bpm", 1, null));
    private static final List<EtapaTreinoLlmDto> OUTRAS_ETAPAS = List.of(
            new EtapaTreinoLlmDto(1, "PRINCIPAL", "pr", 30, 6.0, "136-150 bpm", 1, "5:30-6:00/km"));

    private static final TreinoPlanejadoLlmDto ORIGINAL = new TreinoPlanejadoLlmDto(
            "SEGUNDA", "INTERVALADO", "150-160 bpm", 60, 1.1, 8, "justificativa",
            "45:00", 5.3, "4:30-5:00/km", ETAPAS, "descricao", "Z4", PROVA_ID);

    @Nested
    @DisplayName("comEtapas")
    class ComEtapas {
        @Test
        @DisplayName("troca só etapas; os outros 13 campos, incluindo descricao/zonaAlvo/provaId, ficam")
        void preservaOsDemais() {
            assertThat(ORIGINAL.comEtapas(OUTRAS_ETAPAS)).isEqualTo(new TreinoPlanejadoLlmDto(
                    "SEGUNDA", "INTERVALADO", "150-160 bpm", 60, 1.1, 8, "justificativa",
                    "45:00", 5.3, "4:30-5:00/km", OUTRAS_ETAPAS, "descricao", "Z4", PROVA_ID));
        }
    }

    @Nested
    @DisplayName("comRitmo")
    class ComRitmo {
        @Test
        @DisplayName("troca só ritmoAlvo")
        void preservaOsDemais() {
            assertThat(ORIGINAL.comRitmo("5:00-5:15/km")).isEqualTo(new TreinoPlanejadoLlmDto(
                    "SEGUNDA", "INTERVALADO", "150-160 bpm", 60, 1.1, 8, "justificativa",
                    "45:00", 5.3, "5:00-5:15/km", ETAPAS, "descricao", "Z4", PROVA_ID));
        }
    }

    @Nested
    @DisplayName("comDistancia")
    class ComDistancia {
        @Test
        @DisplayName("troca só distanciaKm (vizinho de duracaoMin e ritmoAlvo, mesmo tipo de nullabilidade)")
        void preservaOsDemais() {
            assertThat(ORIGINAL.comDistancia(7.7)).isEqualTo(new TreinoPlanejadoLlmDto(
                    "SEGUNDA", "INTERVALADO", "150-160 bpm", 60, 1.1, 8, "justificativa",
                    "45:00", 7.7, "4:30-5:00/km", ETAPAS, "descricao", "Z4", PROVA_ID));
        }
    }

    @Nested
    @DisplayName("comDuracao")
    class ComDuracao {
        @Test
        @DisplayName("troca só duracaoMin")
        void preservaOsDemais() {
            assertThat(ORIGINAL.comDuracao("50:00")).isEqualTo(new TreinoPlanejadoLlmDto(
                    "SEGUNDA", "INTERVALADO", "150-160 bpm", 60, 1.1, 8, "justificativa",
                    "50:00", 5.3, "4:30-5:00/km", ETAPAS, "descricao", "Z4", PROVA_ID));
        }
    }

    @Test
    @DisplayName("o original não muda: records são imutáveis e os withers devolvem instância nova")
    void originalIntacto() {
        ORIGINAL.comEtapas(OUTRAS_ETAPAS).comRitmo("x").comDistancia(0.0).comDuracao("00:00");
        assertThat(ORIGINAL).isEqualTo(new TreinoPlanejadoLlmDto(
                "SEGUNDA", "INTERVALADO", "150-160 bpm", 60, 1.1, 8, "justificativa",
                "45:00", 5.3, "4:30-5:00/km", ETAPAS, "descricao", "Z4", PROVA_ID));
    }
}
