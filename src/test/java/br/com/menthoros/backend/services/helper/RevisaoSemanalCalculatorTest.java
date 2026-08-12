package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.enums.MetricasThresholds;
import br.com.menthoros.backend.enums.NivelAderencia;
import br.com.menthoros.backend.enums.RecommendationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    @Nested
    @DisplayName("nextWeekFocusTemplate — fallback determinístico da narrativa (CA-LLM)")
    class TemplateDeFoco {

        @Test
        @DisplayName("RECOVERY fala em reduzir carga, nunca em progredir")
        void recoveryNaoSugereProgressao() {
            String texto = RevisaoSemanalCalculator.nextWeekFocusTemplate(RecommendationType.RECOVERY);

            assertThat(texto).containsIgnoringCase("recuper");
            assertThat(texto).doesNotContainIgnoringCase("aument");
        }

        @Test
        @DisplayName("MAINTAIN fala em manter, nunca em progredir")
        void maintainNaoSugereProgressao() {
            String texto = RevisaoSemanalCalculator.nextWeekFocusTemplate(RecommendationType.MAINTAIN);

            assertThat(texto).containsIgnoringCase("manter");
            assertThat(texto).doesNotContainIgnoringCase("aument");
        }

        @Test
        @DisplayName("PROGRESS propõe evolução gradual")
        void progressPropoeEvolucao() {
            String texto = RevisaoSemanalCalculator.nextWeekFocusTemplate(RecommendationType.PROGRESS);

            assertThat(texto).containsIgnoringCase("gradual");
        }

        @ParameterizedTest
        @EnumSource(RecommendationType.class)
        @DisplayName("todo tipo tem template não-vazio — nenhuma revisão nasce sem foco")
        void todoTipoTemTemplate(RecommendationType tipo) {
            assertThat(RevisaoSemanalCalculator.nextWeekFocusTemplate(tipo)).isNotBlank();
        }

        @Test
        @DisplayName("rejeita tipo nulo em vez de devolver texto vazio")
        void rejeitaTipoNulo() {
            assertThatThrownBy(() -> RevisaoSemanalCalculator.nextWeekFocusTemplate(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("withinConsumptionWindow — janela de validade da revisão (D11)")
    class JanelaDeConsumo {

        @Test
        @DisplayName("revisão da semana imediatamente anterior é consumida")
        void semanaAnteriorEntra() {
            LocalDate planoInicio = LocalDate.of(2026, 7, 20);   // segunda
            LocalDate revisaoFim = LocalDate.of(2026, 7, 19);    // domingo anterior

            assertThat(RevisaoSemanalCalculator.withinConsumptionWindow(revisaoFim, planoInicio)).isTrue();
        }

        @Test
        @DisplayName("no limite da folga (7 dias) ainda entra — absorve encerramento atrasado")
        void limiteDaFolgaEntra() {
            LocalDate planoInicio = LocalDate.of(2026, 7, 20);
            LocalDate revisaoFim = LocalDate.of(2026, 7, 13);    // 7 dias antes do início

            assertThat(RevisaoSemanalCalculator.withinConsumptionWindow(revisaoFim, planoInicio)).isTrue();
        }

        @Test
        @DisplayName("um dia além da folga não entra")
        void alemDaFolgaNaoEntra() {
            LocalDate planoInicio = LocalDate.of(2026, 7, 20);
            LocalDate revisaoFim = LocalDate.of(2026, 7, 12);

            assertThat(RevisaoSemanalCalculator.withinConsumptionWindow(revisaoFim, planoInicio)).isFalse();
        }

        @Test
        @DisplayName("revisão de três semanas atrás (atleta lesionado) não entra")
        void revisaoObsoletaNaoEntra() {
            LocalDate planoInicio = LocalDate.of(2026, 7, 20);
            LocalDate revisaoFim = LocalDate.of(2026, 6, 28);

            assertThat(RevisaoSemanalCalculator.withinConsumptionWindow(revisaoFim, planoInicio)).isFalse();
        }

        @Test
        @DisplayName("revisão do futuro não entra — protege contra data inconsistente")
        void revisaoNoFuturoNaoEntra() {
            LocalDate planoInicio = LocalDate.of(2026, 7, 20);
            LocalDate revisaoFim = LocalDate.of(2026, 7, 26);

            assertThat(RevisaoSemanalCalculator.withinConsumptionWindow(revisaoFim, planoInicio)).isFalse();
        }

        @Test
        @DisplayName("datas nulas não consomem")
        void datasNulasNaoEntram() {
            assertThat(RevisaoSemanalCalculator.withinConsumptionWindow(null, LocalDate.now())).isFalse();
            assertThat(RevisaoSemanalCalculator.withinConsumptionWindow(LocalDate.now(), null)).isFalse();
        }
    }

    @Nested
    @DisplayName("limiares vêm de MetricasThresholds")
    class LimiaresFonteUnica {

        /*
         * Guarda contra o retorno da terceira cópia. Estes campos já foram literais (-25 e -10)
         * "ancorados" na RecoveryCargaSkill por comentário, não por código: mudar a skill não mexia
         * aqui, e nada falhava. As asserções vivem neste arquivo, e não no
         * MetricasThresholdsFonteUnicaTest, porque os campos são package-private — alargar a
         * visibilidade só para o teste seria deixar o teste ditar o desenho.
         */

        @Test
        @DisplayName("o piso de RECOVERY é o do enum")
        void pisoVemDoEnum() {
            assertThat(RevisaoSemanalCalculator.TSB_PISO_RECOVERY.doubleValue())
                    .isEqualTo(MetricasThresholds.TSB_PISO_RECOVERY);
        }

        @Test
        @DisplayName("a faixa de fadiga é a do enum")
        void faixaVemDoEnum() {
            assertThat(RevisaoSemanalCalculator.TSB_FADIGA.doubleValue())
                    .isEqualTo(MetricasThresholds.TSB_ACUMULANDO_FADIGA);
        }
    }
}
