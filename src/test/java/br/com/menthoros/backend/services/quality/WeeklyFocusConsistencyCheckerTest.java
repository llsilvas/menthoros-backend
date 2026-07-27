package br.com.menthoros.backend.services.quality;

import br.com.menthoros.backend.enums.RecommendationType;
import br.com.menthoros.backend.services.helper.RevisaoSemanalCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checker determinístico do foco (D10): a narrativa por LLM não pode contrariar o
 * {@code recommendationType} congelado. Sem esta verificação, "narrativa consistente" seria
 * julgamento subjetivo e o CA-LLM não seria testável.
 */
class WeeklyFocusConsistencyCheckerTest {

    private final WeeklyFocusConsistencyChecker checker = new WeeklyFocusConsistencyChecker();

    @Nested
    @DisplayName("isConsistent — tipos que proíbem progressão")
    class TiposRestritivos {

        @ParameterizedTest
        @EnumSource(value = RecommendationType.class, names = {"RECOVERY", "MAINTAIN"})
        @DisplayName("reprova narrativa que manda aumentar carga")
        void reprovaAumento(RecommendationType tipo) {
            assertThat(checker.isConsistent("Aumente o volume em 10% nesta semana.", tipo)).isFalse();
        }

        @ParameterizedTest
        @EnumSource(value = RecommendationType.class, names = {"RECOVERY", "MAINTAIN"})
        @DisplayName("reprova narrativa que manda progredir")
        void reprovaProgressao(RecommendationType tipo) {
            assertThat(checker.isConsistent("Hora de progredir para treinos mais longos.", tipo)).isFalse();
        }

        @Test
        @DisplayName("aprova narrativa de recuperação sob RECOVERY")
        void aprovaRecuperacao() {
            assertThat(checker.isConsistent(
                    "Semana de recuperação: reduza o volume e evite intensidade.",
                    RecommendationType.RECOVERY)).isTrue();
        }

        @Test
        @DisplayName("não confunde 'evite aumentar' com ordem de aumentar")
        void naoConfundeNegacao() {
            assertThat(checker.isConsistent(
                    "Mantenha o volume atual e evite aumentar a intensidade.",
                    RecommendationType.MAINTAIN)).isFalse();
        }
    }

    @Nested
    @DisplayName("isConsistent — PROGRESS")
    class TipoProgressivo {

        @Test
        @DisplayName("aprova narrativa de progressão sob PROGRESS")
        void aprovaProgressao() {
            assertThat(checker.isConsistent(
                    "Aumente o volume gradualmente, preservando os treinos-chave.",
                    RecommendationType.PROGRESS)).isTrue();
        }
    }

    @Nested
    @DisplayName("isConsistent — entradas degeneradas")
    class EntradasDegeneradas {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("reprova narrativa nula, vazia ou em branco")
        void reprovaVazio(String narrativa) {
            assertThat(checker.isConsistent(narrativa, RecommendationType.MAINTAIN)).isFalse();
        }

        @Test
        @DisplayName("reprova quando o tipo é nulo — sem tipo não há o que verificar")
        void reprovaTipoNulo() {
            assertThat(checker.isConsistent("Qualquer texto.", null)).isFalse();
        }
    }

    @Nested
    @DisplayName("invariante template × checker")
    class InvarianteTemplate {

        @ParameterizedTest
        @EnumSource(RecommendationType.class)
        @DisplayName("o template de cada tipo passa no próprio checker")
        void templateSempreConsistente(RecommendationType tipo) {
            assertThat(checker.isConsistent(RevisaoSemanalCalculator.nextWeekFocusTemplate(tipo), tipo))
                    .isTrue();
        }
    }
}
