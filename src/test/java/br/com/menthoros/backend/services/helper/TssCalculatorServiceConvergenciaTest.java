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
 * <p><b>Por que estes testes existem.</b> O planejado usava {@code min × RPE² / 90} e o realizado
 * {@code h × IF² × 100} — duas fórmulas para a mesma grandeza, divergindo de 2,4× a 6× conforme o
 * RPE (BUG-CONF-001). A classe fixa os valores absolutos dos dois caminhos <b>e</b> a igualdade
 * entre eles: só a igualdade não bastaria, porque se ambos quebrassem da mesma forma ela
 * continuaria verde.
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
    @DisplayName("valores absolutos fixados — os dois caminhos")
    class ValoresAbsolutos {

        // Fixar os valores absolutos, e não só a igualdade entre os caminhos: se ambos quebrassem
        // da mesma forma, o teste de convergência continuaria verde e não perceberíamos.
        @ParameterizedTest(name = "planejado {0}min RPE {1} = {2}")
        @CsvSource({
                // Bordas da faixa primeiro: RPE 1 e 10 são onde o clamp de IF viveria. Ele é inerte
                // aqui (RPE 1 -> IF 0,45 = MIN_IF_RPE; RPE 10 -> IF 1,25 < MAX_IF 1,50), e fixar os
                // valores é o que prova isso — se o mapeamento mudar e o clamp passar a morder, cai.
                "60,  1,  20",
                "60,  3,  36",
                "60,  5,  54",
                "60,  7,  81",
                "60,  9, 127",
                "60, 10, 156",
                // Durações fora de 60min: sem elas, um bug no fator horas passaria despercebido,
                // porque em 1h a duração some da conta.
                "30,  5,  27",
                "90,  7, 122",
        })
        @DisplayName("planejado usa h × IF² × 100 (era min × RPE² / 90 — BUG-CONF-001)")
        void planejado(int minutos, int rpe, int esperado) {
            assertThat(tssPlanejado(minutos, rpe)).isEqualTo(esperado);
        }

        @ParameterizedTest(name = "realizado {0}min RPE {1} = {2}")
        @CsvSource({
                // Bordas da faixa primeiro: RPE 1 e 10 são onde o clamp de IF viveria. Ele é inerte
                // aqui (RPE 1 -> IF 0,45 = MIN_IF_RPE; RPE 10 -> IF 1,25 < MAX_IF 1,50), e fixar os
                // valores é o que prova isso — se o mapeamento mudar e o clamp passar a morder, cai.
                "60,  1,  20",
                "60,  3,  36",
                "60,  5,  54",
                "60,  7,  81",
                "60,  9, 127",
                "60, 10, 156",
                // Durações fora de 60min: sem elas, um bug no fator horas passaria despercebido,
                // porque em 1h a duração some da conta.
                "30,  5,  27",
                "90,  7, 122",
        })
        @DisplayName("realizado usa h × IF² × 100 — inalterado pela correção")
        void realizado(int minutos, int rpe, int esperado) {
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
