package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.enums.NivelAderencia;
import br.com.menthoros.backend.enums.RecommendationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Lógica determinística da consolidação (CA1b, CA2, CA2b, CA2c, CA3, CA3b) — pura, sem DB.
 * Regras congeladas em ADR-0006 / design D4–D5.
 */
class RevisaoSemanalCalculatorTest {

    @Nested
    @DisplayName("completionRate")
    class PercentualRealizacao {

        @Test
        @DisplayName("razão realizados/planejados em 0–100 com 2 casas")
        void razaoSimples() {
            assertThat(RevisaoSemanalCalculator.completionRate(4, 3)).isEqualByComparingTo("75.00");
        }

        @Test
        @DisplayName("sem treinos planejados → 0 (degenerado; o gate de sufficientData cobre)")
        void semPlanejados() {
            assertThat(RevisaoSemanalCalculator.completionRate(0, 0)).isEqualByComparingTo("0.00");
        }
    }

    @Nested
    @DisplayName("nivelAderencia — cortes ALTA/MEDIA/BAIXA + override de criticidade (CA1b)")
    class Aderencia {

        @ParameterizedTest(name = "{0}% (crítico={1}) → {2}")
        @CsvSource({
                "95, false, ALTA",
                "90, false, ALTA",
                "89, false, MEDIA",
                "60, false, MEDIA",
                "59, false, BAIXA",
                "0,  false, BAIXA",
                "95, true,  BAIXA"   // crítico faltando força BAIXA mesmo com % alto
        })
        void cortes(BigDecimal percentual, boolean criticoFaltando, NivelAderencia esperado) {
            assertThat(RevisaoSemanalCalculator.nivelAderencia(percentual, criticoFaltando)).isEqualTo(esperado);
        }
    }

    @Nested
    @DisplayName("treinoCritico — fatorImpacto ≥ 1.15")
    class Critico {

        @ParameterizedTest(name = "fatorImpacto {0} → crítico={1}")
        @CsvSource({"1.15, true", "1.4, true", "1.6, true", "1.0, false", "0.5, false"})
        void limiar(double fatorImpacto, boolean esperado) {
            assertThat(RevisaoSemanalCalculator.treinoCritico(fatorImpacto)).isEqualTo(esperado);
        }
    }

    @Nested
    @DisplayName("sufficientData (CA3)")
    class DadosSuficientes {

        @Test
        @DisplayName("true com ≥2 treinos realizados e tsbFim presente")
        void suficiente() {
            assertThat(RevisaoSemanalCalculator.sufficientData(2, new BigDecimal("-5"))).isTrue();
        }

        @Test
        @DisplayName("false com <2 treinos realizados")
        void poucosTreinos() {
            assertThat(RevisaoSemanalCalculator.sufficientData(1, new BigDecimal("-5"))).isFalse();
        }

        @Test
        @DisplayName("false quando tsbFim é nulo (sem ponto de TSB válido)")
        void tsbNulo() {
            assertThat(RevisaoSemanalCalculator.sufficientData(3, null)).isFalse();
        }
    }

    @Nested
    @DisplayName("recommendationType — árvore determinística (CA2/CA2b/CA2c/CA3b)")
    class Arvore {

        static Stream<Arguments> casos() {
            return Stream.of(
                    // RECOVERY — TSB no piso, qualquer aderência
                    arguments(NivelAderencia.ALTA, new BigDecimal("-25"), true, RecommendationType.RECOVERY),
                    arguments(NivelAderencia.MEDIA, new BigDecimal("-30"), true, RecommendationType.RECOVERY),
                    // RECOVERY — baixa aderência com fadiga (TSB ≤ −10)
                    arguments(NivelAderencia.BAIXA, new BigDecimal("-10"), false, RecommendationType.RECOVERY),
                    arguments(NivelAderencia.BAIXA, new BigDecimal("-12"), false, RecommendationType.RECOVERY),
                    // PROGRESS — semana boa
                    arguments(NivelAderencia.ALTA, new BigDecimal("-10"), true, RecommendationType.PROGRESS),
                    arguments(NivelAderencia.ALTA, new BigDecimal("0"), true, RecommendationType.PROGRESS),
                    arguments(NivelAderencia.ALTA, new BigDecimal("5"), true, RecommendationType.PROGRESS),
                    // PROGRESS bloqueado — dados insuficientes
                    arguments(NivelAderencia.ALTA, new BigDecimal("0"), false, RecommendationType.MAINTAIN),
                    // PROGRESS bloqueado — TSB abaixo da faixa
                    arguments(NivelAderencia.ALTA, new BigDecimal("-11"), true, RecommendationType.MAINTAIN),
                    // MAINTAIN — intermediário
                    arguments(NivelAderencia.MEDIA, new BigDecimal("-5"), true, RecommendationType.MAINTAIN),
                    // MAINTAIN — baixa aderência SEM fadiga (TSB > −10)
                    arguments(NivelAderencia.BAIXA, new BigDecimal("-5"), true, RecommendationType.MAINTAIN),
                    // CA3b — tsbFim nulo cai em MAINTAIN (ramos numéricos não se aplicam)
                    arguments(NivelAderencia.ALTA, null, true, RecommendationType.MAINTAIN)
            );
        }

        @ParameterizedTest(name = "aderência={0}, tsbFim={1}, dados={2} → {3}")
        @MethodSource("casos")
        void arvore(NivelAderencia status, BigDecimal tsbFim, boolean sufficientData, RecommendationType esperado) {
            assertThat(RevisaoSemanalCalculator.recommendationType(status, tsbFim, sufficientData))
                    .isEqualTo(esperado);
        }
    }
}
