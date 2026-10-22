package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.config.core.WorkoutAnalysisProperties;
import br.com.menthoros.backend.dto.output.AthleteWorkoutAnalysisOutputDto.Executado;
import br.com.menthoros.backend.dto.output.AthleteWorkoutAnalysisOutputDto.Planejado;
import br.com.menthoros.backend.enums.WorkoutPlanVerdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Testes unitários para {@link WorkoutPlanVerdictCalculator} — regra determinística de aderência
 * ao plano (add-athlete-workout-verdict-chip, D2). Cenários replicam o spec delta de
 * {@code athlete-workout-analysis}; chamada direta ao colaborador, sem contexto Spring.
 */
@DisplayName("WorkoutPlanVerdictCalculator")
class WorkoutPlanVerdictCalculatorTest {

    /** Valores explícitos (não defaults implícitos) — a fonte de verdade do default de produção
     * é {@code WorkoutAnalysisProperties.Verdict}, exercitada à parte no nested abaixo. */
    private final WorkoutPlanVerdictCalculator calculator = new WorkoutPlanVerdictCalculator(15.0, 2);

    private static Executado executado(Long duracaoMin, Double distanciaKm, Integer rpe) {
        return new Executado(duracaoMin, bd(distanciaKm), rpe);
    }

    private static Planejado planejado(Long duracaoMin, Double distanciaKm, Integer rpeEsperado) {
        return new Planejado(duracaoMin, bd(distanciaKm), rpeEsperado);
    }

    private static BigDecimal bd(Double valor) {
        return valor == null ? null : BigDecimal.valueOf(valor);
    }

    @Nested
    @DisplayName("cenários do spec")
    class CenariosDoSpec {

        @Test
        @DisplayName("treino como planejado -> DENTRO_DO_PLANO")
        void treinoComoPlanejado() {
            var resultado = calculator.calcular(
                    executado(29L, 4.0, 5),
                    planejado(30L, 4.0, 5)
            );
            assertThat(resultado).isEqualTo(WorkoutPlanVerdict.DENTRO_DO_PLANO);
        }

        @Test
        @DisplayName("esforço elevado tem precedência sobre duração abaixo -> ESFORCO_ACIMA_DO_ESPERADO")
        void esforcoElevadoTemPrecedencia() {
            var resultado = calculator.calcular(
                    executado(22L, null, 7),
                    planejado(30L, null, 5)
            );
            assertThat(resultado).isEqualTo(WorkoutPlanVerdict.ESFORCO_ACIMA_DO_ESPERADO);
        }

        @Test
        @DisplayName("volume de duração abaixo -> ABAIXO_DO_PLANO")
        void volumeAbaixo() {
            var resultado = calculator.calcular(
                    executado(45L, null, 5),
                    planejado(60L, null, 5)
            );
            assertThat(resultado).isEqualTo(WorkoutPlanVerdict.ABAIXO_DO_PLANO);
        }

        @Test
        @DisplayName("volume de distância acima -> ACIMA_DO_PLANO")
        void volumeAcima() {
            var resultado = calculator.calcular(
                    executado(null, 10.0, 5),
                    planejado(null, 8.0, 5)
            );
            assertThat(resultado).isEqualTo(WorkoutPlanVerdict.ACIMA_DO_PLANO);
        }

        @Test
        @DisplayName("sem planejado vinculado -> null")
        void semPlanejado() {
            var resultado = calculator.calcular(executado(30L, 5.0, 5), null);
            assertThat(resultado).isNull();
        }

        @Test
        @DisplayName("executado nulo -> NullPointerException (contrato: executado nunca é nulo)")
        void executadoNuloLancaExcecao() {
            assertThatNullPointerException()
                    .isThrownBy(() -> calculator.calcular(null, planejado(30L, 5.0, 5)));
        }

        @Test
        @DisplayName("distância planejada ausente é ignorada; veredito sai de duração e RPE")
        void campoAusenteNoPlanejadoEIgnorado() {
            var resultado = calculator.calcular(
                    executado(30L, 7.0, 5),
                    planejado(30L, null, 5)
            );
            assertThat(resultado).isEqualTo(WorkoutPlanVerdict.DENTRO_DO_PLANO);
        }

        @Test
        @DisplayName("desvio misto (duração acima, distância abaixo) -> ACIMA_DO_PLANO")
        void desvioMistoVirAcima() {
            var resultado = calculator.calcular(
                    executado(75L, 8.0, 5),
                    planejado(60L, 10.0, 5)
            );
            assertThat(resultado).isEqualTo(WorkoutPlanVerdict.ACIMA_DO_PLANO);
        }

        @Test
        @DisplayName("desvio misto no sentido oposto (duração abaixo, distância acima) -> ACIMA_DO_PLANO")
        void desvioMistoOpostoTambemVirAcima() {
            var resultado = calculator.calcular(
                    executado(45L, 12.0, 5),
                    planejado(60L, 10.0, 5)
            );
            assertThat(resultado).isEqualTo(WorkoutPlanVerdict.ACIMA_DO_PLANO);
        }

        @Test
        @DisplayName("distância planejada sem contrapartida executada -> null (dado incompleto)")
        void dadoIncompletoNaoGarenteDentroDoPlano() {
            var resultado = calculator.calcular(
                    executado(60L, null, 5),
                    planejado(60L, 10.0, 5)
            );
            assertThat(resultado).isNull();
        }
    }

    @Nested
    @DisplayName("bordas de tolerância (85% / 115%)")
    class BordasDeTolerancia {

        @Test
        @DisplayName("exatamente 85% do planejado -> DENTRO_DO_PLANO (limite inclusivo)")
        void exatamente85PorCento() {
            var resultado = calculator.calcular(
                    executado(51L, null, 5),
                    planejado(60L, null, 5)
            );
            assertThat(resultado).isEqualTo(WorkoutPlanVerdict.DENTRO_DO_PLANO);
        }

        @Test
        @DisplayName("exatamente 115% do planejado -> DENTRO_DO_PLANO (limite inclusivo)")
        void exatamente115PorCento() {
            var resultado = calculator.calcular(
                    executado(69L, null, 5),
                    planejado(60L, null, 5)
            );
            assertThat(resultado).isEqualTo(WorkoutPlanVerdict.DENTRO_DO_PLANO);
        }

        @Test
        @DisplayName("distância 6,90 km vs. planejado 6,00 km (exatamente 115%) -> DENTRO_DO_PLANO " +
                "(achado Codex: 6.9/6.0 em double vira 1.1500000000000001 e cruza o limite)")
        void exatamente115PorCentoComDistanciaDecimal() {
            var resultado = calculator.calcular(
                    executado(null, 6.9, 5),
                    planejado(null, 6.0, 5)
            );
            assertThat(resultado).isEqualTo(WorkoutPlanVerdict.DENTRO_DO_PLANO);
        }

        @Test
        @DisplayName("abaixo de 85% -> ABAIXO_DO_PLANO")
        void abaixoDoLimiteInferior() {
            var resultado = calculator.calcular(
                    executado(50L, null, 5),
                    planejado(60L, null, 5)
            );
            assertThat(resultado).isEqualTo(WorkoutPlanVerdict.ABAIXO_DO_PLANO);
        }

        @Test
        @DisplayName("acima de 115% -> ACIMA_DO_PLANO")
        void acimaDoLimiteSuperior() {
            var resultado = calculator.calcular(
                    executado(70L, null, 5),
                    planejado(60L, null, 5)
            );
            assertThat(resultado).isEqualTo(WorkoutPlanVerdict.ACIMA_DO_PLANO);
        }
    }

    @Nested
    @DisplayName("borda do RPE (delta = 2)")
    class BordaDoRpe {

        @Test
        @DisplayName("rpe == esperado + 2 -> ESFORCO_ACIMA_DO_ESPERADO")
        void deltaExato() {
            var resultado = calculator.calcular(
                    executado(30L, null, 7),
                    planejado(30L, null, 5)
            );
            assertThat(resultado).isEqualTo(WorkoutPlanVerdict.ESFORCO_ACIMA_DO_ESPERADO);
        }

        @Test
        @DisplayName("rpe == esperado + 1 -> não é esforço acima; cai na comparação de volume")
        void deltaAbaixoDoLimiar() {
            var resultado = calculator.calcular(
                    executado(30L, null, 6),
                    planejado(30L, null, 5)
            );
            assertThat(resultado).isEqualTo(WorkoutPlanVerdict.DENTRO_DO_PLANO);
        }
    }

    @Nested
    @DisplayName("campos ausentes e planejado zero")
    class CamposAusentesEPlanejadoZero {

        @Test
        @DisplayName("planejado com duração zero é ignorado como não aplicável")
        void duracaoPlanejadaZeroEIgnorada() {
            var resultado = calculator.calcular(
                    executado(30L, 8.0, 5),
                    planejado(0L, 8.0, 5)
            );
            assertThat(resultado).isEqualTo(WorkoutPlanVerdict.DENTRO_DO_PLANO);
        }

        @Test
        @DisplayName("sem RPE em nenhum dos lados, veredito sai de duração e distância")
        void semRpeEmNenhumLado() {
            var resultado = calculator.calcular(
                    executado(45L, 8.0, null),
                    planejado(60L, 8.0, null)
            );
            assertThat(resultado).isEqualTo(WorkoutPlanVerdict.ABAIXO_DO_PLANO);
        }

        @Test
        @DisplayName("nenhuma dimensão comparável -> null")
        void nenhumaDimensaoComparavel() {
            var resultado = calculator.calcular(
                    executado(30L, 8.0, 5),
                    planejado(null, null, null)
            );
            assertThat(resultado).isNull();
        }
    }

    @Nested
    @DisplayName("construtor a partir de WorkoutAnalysisProperties")
    class ConstrutorComProperties {

        @Test
        @DisplayName("usa toleranciaPct e deltaRpe configurados nas properties")
        void usaLimiaresConfigurados() {
            WorkoutAnalysisProperties properties = new WorkoutAnalysisProperties();
            properties.getVerdict().setToleranciaPct(10.0);
            properties.getVerdict().setDeltaRpe(1);
            var calculadoraConfigurada = new WorkoutPlanVerdictCalculator(properties);

            // 12% abaixo: dentro dos 15% default, mas fora dos 10% configurados
            var resultado = calculadoraConfigurada.calcular(
                    executado(53L, null, 5),
                    planejado(60L, null, 5)
            );
            assertThat(resultado).isEqualTo(WorkoutPlanVerdict.ABAIXO_DO_PLANO);
        }
    }
}
