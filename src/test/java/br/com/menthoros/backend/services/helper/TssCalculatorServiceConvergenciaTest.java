package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.entity.TreinoRealizado;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Convergência entre o TSS de treino <b>planejado</b> e o de treino <b>realizado</b>
 * (BUG-CONF-001).
 *
 * <p><b>O seam.</b> A comparação só é legítima contra o caminho RPE do realizado, sem fator de
 * impacto — um treino planejado não tem FC, pace, etapas nem elevação, e comparar contra o pipeline
 * completo divergiria de propósito. O isolamento é obtido deixando {@code tipoTreino} nulo: nesse
 * caso {@code aplicarFatorImpactoTreino} devolve a base intacta, então {@code calcularTss} expõe
 * exatamente {@code h × IF² × 100}.
 *
 * <p><b>Por que estes testes existem.</b> Hoje o planejado usa {@code min × RPE² / 90} e o realizado
 * usa {@code h × IF² × 100} — duas fórmulas para a mesma grandeza, divergindo por um fator que
 * varia com o RPE. A classe fixa o comportamento atual (nos testes de caracterização) e afirma o
 * comportamento desejado (no teste de convergência), de modo que a correção da task 2.1 mova
 * exatamente um conjunto e não o outro.
 */
class TssCalculatorServiceConvergenciaTest {

    private final TssCalculatorService service = new TssCalculatorService();

    /** Realizado pelo caminho RPE puro: sem FC, sem pace, sem tipo — logo, sem fator de impacto. */
    private int tssRealizadoPorRpe(int minutos, int rpe) {
        var treino = new TreinoRealizado();
        treino.setDuracaoMin(Duration.ofMinutes(minutos));
        treino.setPercepcaoEsforco(rpe);
        return service.calcularTss(treino);
    }

    private int tssPlanejado(int minutos, int rpe) {
        return service.calcularTssEstimado(Duration.ofMinutes(minutos), rpe);
    }

    @Nested
    @DisplayName("caracterização — comportamento ANTES da correção")
    class Caracterizacao {

        @ParameterizedTest(name = "planejado {0}min RPE {1} = {2}")
        @CsvSource({
                "60, 3,  6",
                "60, 5, 17",
                "60, 7, 33",
                "60, 9, 54",
        })
        @DisplayName("planejado usa min × RPE² / 90 — é este o valor que a correção vai mudar")
        void planejadoHoje(int minutos, int rpe, int esperado) {
            assertThat(tssPlanejado(minutos, rpe)).isEqualTo(esperado);
        }

        @ParameterizedTest(name = "realizado {0}min RPE {1} = {2}")
        @CsvSource({
                "60, 3,  36",
                "60, 5,  54",
                "60, 7,  81",
                "60, 9, 127",
        })
        @DisplayName("realizado usa h × IF² × 100 — este NÃO pode mudar com a correção")
        void realizadoHoje(int minutos, int rpe, int esperado) {
            assertThat(tssRealizadoPorRpe(minutos, rpe)).isEqualTo(esperado);
        }

        @Test
        @DisplayName("duração nula resulta em zero, nos dois caminhos")
        void duracaoNulaEhZero() {
            assertThat(service.calcularTssEstimado(null, 5)).isZero();
            assertThat(service.calcularTss(new TreinoRealizado())).isZero();
        }

        @Test
        @DisplayName("RPE nulo no planejado assume 5")
        void rpeNuloAssumeCinco() {
            assertThat(service.calcularTssEstimado(Duration.ofMinutes(60), null))
                    .isEqualTo(tssPlanejado(60, 5));
        }
    }

    @Nested
    @DisplayName("convergência — comportamento DESEJADO (CA1)")
    class Convergencia {

        @ParameterizedTest(name = "{0}min RPE {1}: planejado == realizado")
        @CsvSource({
                "30, 3", "60, 3", "90, 3",
                "30, 5", "60, 5", "90, 5",
                "30, 7", "60, 7", "90, 7",
                "30, 9", "60, 9", "90, 9",
                "45, 1", "45, 10",
        })
        @DisplayName("mesma duração e mesmo RPE produzem o mesmo TSS nos dois caminhos")
        void planejadoConvergeComRealizado(int minutos, int rpe) {
            int planejado = tssPlanejado(minutos, rpe);
            int realizado = tssRealizadoPorRpe(minutos, rpe);

            assertThat(planejado)
                    .as("planejado (%d) e realizado (%d) para %dmin RPE %d — divergência de %.1fx",
                            planejado, realizado, minutos, rpe,
                            planejado == 0 ? 0.0 : (double) realizado / planejado)
                    .isEqualTo(realizado);
        }
    }
}
