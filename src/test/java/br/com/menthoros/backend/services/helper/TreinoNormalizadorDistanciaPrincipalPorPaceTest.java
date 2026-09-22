package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.llm.EtapaTreinoLlmDto;
import br.com.menthoros.backend.dto.llm.TreinoPlanejadoLlmDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TreinoNormalizador#distanciaPrincipalPorPace} (fix-etapas-continuos-pace): a LLM concentra a
 * distância do treino na PRINCIPAL — caso real 22/09, REGENERATIVO com 5,5 km em 30min a 7:28-7:55.
 */
@DisplayName("TreinoNormalizador — distância da PRINCIPAL pelo pace")
class TreinoNormalizadorDistanciaPrincipalPorPaceTest {

    private TreinoNormalizador normalizador;

    @BeforeEach
    void setUp() {
        normalizador = new TreinoNormalizador(new PaceValidator());
    }

    @Nested
    @DisplayName("distanciaPrincipalPorPace")
    class DistanciaPrincipalPorPace {

        @Test
        @DisplayName("CA2: PRINCIPAL com ritmoAlvo recebe duração ÷ pace médio")
        void principalComRitmoRecebeDistanciaDoPace() {
            var treino = continuo(etapa("PRINCIPAL", 30, 5.5, "7:28-7:55/km"));

            var resultado = normalizador.distanciaPrincipalPorPace(treino);

            // pace médio de 7:28-7:55 = 7,69 min/km → 30 / 7,69 = 3,90 km
            assertThat(resultado.etapas().getFirst().distanciaKm()).isEqualTo(3.9);
        }

        @Test
        @DisplayName("CA3: PRINCIPAL sem ritmoAlvo mantém a distância da LLM")
        void principalSemRitmoMantemDistancia() {
            var original = etapa("PRINCIPAL", 30, 5.5, null);

            var resultado = normalizador.distanciaPrincipalPorPace(continuo(original));

            assertThat(resultado.etapas().getFirst()).isEqualTo(original);
        }

        @Test
        @DisplayName("PRINCIPAL sem duração mantém a distância da LLM")
        void principalSemDuracaoMantemDistancia() {
            var original = etapa("PRINCIPAL", null, 5.5, "7:28-7:55/km");

            var resultado = normalizador.distanciaPrincipalPorPace(continuo(original));

            assertThat(resultado.etapas().getFirst()).isEqualTo(original);
        }

        @Test
        @DisplayName("CA6: LONGO com duas PRINCIPAL — cada uma recebe a própria distância")
        void cadaPrincipalRecebeAPropriaDistancia() {
            var treino = continuo(
                    etapa("PRINCIPAL", 40, 8.0, "6:45-7:06/km"),
                    etapa("PRINCIPAL", 10, 2.0, "6:00-6:10/km"));

            var resultado = normalizador.distanciaPrincipalPorPace(treino);

            // 40 / 6,925 = 5,78; 10 / 6,083 = 1,64
            assertThat(resultado.etapas()).extracting(EtapaTreinoLlmDto::distanciaKm).containsExactly(5.78, 1.64);
        }

        @Test
        @DisplayName("aquecimento e desaquecimento não são tocados (ficam com corrigir-temporais)")
        void naoTocaEtapasQueNaoSaoPrincipal() {
            var aquec = etapa("AQUECIMENTO", 10, 0.0, "7:28-7:55/km");
            var desaq = etapa("DESAQUECIMENTO", 5, 0.5, null);

            var resultado = normalizador.distanciaPrincipalPorPace(continuo(aquec, desaq));

            assertThat(resultado.etapas()).containsExactly(aquec, desaq);
        }
    }

    private static EtapaTreinoLlmDto etapa(String tipo, Integer duracaoMin, Double distanciaKm, String ritmoAlvo) {
        return new EtapaTreinoLlmDto(1, tipo, tipo.toLowerCase(), duracaoMin, distanciaKm, null, 1, ritmoAlvo);
    }

    private static TreinoPlanejadoLlmDto continuo(EtapaTreinoLlmDto... etapas) {
        return new TreinoPlanejadoLlmDto("SEGUNDA", "REGENERATIVO", null, null, null, null, null,
                "45:00", 6.0, "7:28-7:55/km", List.of(etapas));
    }
}
