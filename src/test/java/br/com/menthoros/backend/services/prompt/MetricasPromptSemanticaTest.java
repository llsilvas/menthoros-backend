package br.com.menthoros.backend.services.prompt;

import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.enums.DiaSemana;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes semânticos para MetricasPromptFormatter.
 *
 * <p>Verifica que o prompt de IA exibe e usa {@code tsbProntidaoAtual} (pré-treino)
 * como "TSB de Prontidão" e distingue corretamente de {@code tsbPosCargaAtual}.</p>
 *
 * <p>TDD: testes escritos em RED antes da implementação.</p>
 */
class MetricasPromptSemanticaTest {

    private MetricasPromptFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new MetricasPromptFormatter();
    }

    @Test
    @DisplayName("formatarMetricas() exibe TSB de Prontidão (pré-treino), não TSB pós-carga")
    void formatter_exibeTsbProntidao_naoPosAcarga() {
        PlanoMetaDados meta = PlanoMetaDados.builder()
                .tsbProntidaoAtual(+5.0)
                .tsbPosCargaAtual(-3.0)
                .tsbAtual(-3.0)
                .ctlAtual(30.0)
                .atlAtual(20.0)
                .rampRateAtual(4.0)
                .diasConsecutivosTreino(2)
                .diasDesdeUltimoDescanso(2)
                .semanasProgressaoContinua(3)
                .diaPreferidoLongo(DiaSemana.DOMINGO)
                .build();

        String prompt = formatter.formatarMetricas(meta);

        assertThat(prompt)
                .as("Prompt deve usar rótulo 'Prontidão' para o TSB pré-treino")
                .containsIgnoringCase("Prontidão");

        // Verifica que o valor 5.0 aparece no prompt (aceita vírgula ou ponto como separador decimal)
        assertThat(prompt)
                .as("Prompt deve exibir o valor 5.0 do tsbProntidaoAtual")
                .satisfiesAnyOf(
                        p -> assertThat(p).contains("5.0"),
                        p -> assertThat(p).contains("5,0")
                );
    }

    @Test
    @DisplayName("formatarMetricas() inclui TSB Pós-carga separado quando disponível")
    void formatter_incluiTsbPosCarga_separado() {
        PlanoMetaDados meta = PlanoMetaDados.builder()
                .tsbProntidaoAtual(+5.0)
                .tsbPosCargaAtual(-3.0)
                .tsbAtual(-3.0)
                .ctlAtual(30.0)
                .atlAtual(20.0)
                .rampRateAtual(4.0)
                .diasConsecutivosTreino(2)
                .diasDesdeUltimoDescanso(2)
                .semanasProgressaoContinua(3)
                .diaPreferidoLongo(DiaSemana.DOMINGO)
                .build();

        String prompt = formatter.formatarMetricas(meta);

        assertThat(prompt)
                .as("Prompt deve indicar TSB Pós-carga separadamente quando disponível")
                .satisfiesAnyOf(
                        p -> assertThat(p).containsIgnoringCase("Pós-carga"),
                        p -> assertThat(p).containsIgnoringCase("Pos-carga"),
                        p -> assertThat(p).containsIgnoringCase("pós carga")
                );
    }

    @Test
    @DisplayName("formatarMetricas() usa tsbProntidaoAtual para interpretação semântica")
    void formatter_usaProntidaoParaInterpretacao() {
        // Prontidão = +8 (forma ideal), pós-carga = -30 (fadiga crítica)
        // O prompt deve refletir a interpretação de prontidão (+8), não pós-carga (-30)
        PlanoMetaDados meta = PlanoMetaDados.builder()
                .tsbProntidaoAtual(+8.0)
                .tsbPosCargaAtual(-30.0)
                .tsbAtual(-30.0)
                .ctlAtual(40.0)
                .atlAtual(25.0)
                .rampRateAtual(3.0)
                .diasConsecutivosTreino(2)
                .diasDesdeUltimoDescanso(2)
                .semanasProgressaoContinua(4)
                .diaPreferidoLongo(DiaSemana.SABADO)
                .build();

        String prompt = formatter.formatarMetricas(meta);

        // A interpretação deve refletir o estado de prontidão (+8 = forma ideal)
        // Não deve indicar fadiga crítica (que seria da pós-carga)
        assertThat(prompt)
                .as("Prompt deve usar tsbProntidaoAtual (+8) para interpretação, não pós-carga (-30)")
                .doesNotContain("FADIGA CRÍTICA");
    }

    @Test
    @DisplayName("formatarMetricas() sem tsbPosCargaAtual não deve quebrar")
    void formatter_semTsbPosCarga_naoQuebra() {
        PlanoMetaDados meta = PlanoMetaDados.builder()
                .tsbProntidaoAtual(+5.0)
                .tsbPosCargaAtual(null)
                .tsbAtual(+5.0)
                .ctlAtual(30.0)
                .atlAtual(20.0)
                .rampRateAtual(4.0)
                .diasConsecutivosTreino(2)
                .diasDesdeUltimoDescanso(2)
                .semanasProgressaoContinua(3)
                .diaPreferidoLongo(DiaSemana.DOMINGO)
                .build();

        String prompt = formatter.formatarMetricas(meta);

        assertThat(prompt)
                .as("Prompt não deve quebrar quando tsbPosCargaAtual é nulo")
                .isNotNull()
                .isNotEmpty()
                .containsIgnoringCase("Prontidão");
    }
}
