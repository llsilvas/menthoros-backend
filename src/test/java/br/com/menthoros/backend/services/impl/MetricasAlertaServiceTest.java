package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.enums.DiaSemana;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MetricasAlertaServiceTest {

    private final MetricasAlertaService service = new MetricasAlertaService();

    @Test
    /**
     * Documenta o bug do ISSUE-01 no cenário mais crítico:
     * para TSB abaixo do limiar crítico (-35), o status deve ser "FADIGA CRÍTICA".
     *
     * Esperado: "FADIGA CRÍTICA"
     * Comportamento atual (antes do fix): tende a retornar "FADIGA ALTA" por causa do check genérico de sobrecarga.
     */
    void deveRetornarFadigaCriticaQuandoTsbAbaixoDoCritico() {
        var metaDados = PlanoMetaDados.builder()
                .diaPreferidoLongo(DiaSemana.DOMINGO)
                .tsbAtual(-40.0)
                .ctlAtual(50.0)
                .rampRateAtual(0.0)
                .diasConsecutivosTreino(0)
                .semanasProgressaoContinua(0)
                .build();

        var resultado = service.analisarMetricas(metaDados);
        assertEquals("FADIGA CRÍTICA", resultado.statusGeral());
    }

    @Test
    /**
     * Valida a faixa intermediária de sobrecarga:
     * para -35 < TSB <= -30, o status deve ser "FADIGA ALTA" (não crítico).
     */
    void deveRetornarFadigaAltaQuandoTsbEntreSobrecargaECritico() {
        var metaDados = PlanoMetaDados.builder()
                .diaPreferidoLongo(DiaSemana.DOMINGO)
                .tsbAtual(-32.0)
                .ctlAtual(50.0)
                .rampRateAtual(0.0)
                .diasConsecutivosTreino(0)
                .semanasProgressaoContinua(0)
                .build();

        var resultado = service.analisarMetricas(metaDados);
        assertEquals("FADIGA ALTA", resultado.statusGeral());
    }

    @Test
    /**
     * Garante que, sem alertas compostos, a classificação por FaixaTsb é respeitada:
     * para TSB em torno de -15, o status esperado é "ACUMULANDO FADIGA".
     */
    void deveClassificarAcumulandoFadigaQuandoTsbModerado() {
        var metaDados = PlanoMetaDados.builder()
                .diaPreferidoLongo(DiaSemana.DOMINGO)
                .tsbAtual(-15.0)
                .ctlAtual(50.0)
                .rampRateAtual(0.0)
                .diasConsecutivosTreino(0)
                .semanasProgressaoContinua(0)
                .build();

        var resultado = service.analisarMetricas(metaDados);
        assertEquals("ACUMULANDO FADIGA", resultado.statusGeral());
    }

    @Test
    /**
     * Valida o status composto quando há sobrecarga + progressão rápida (ramp rate crítico):
     * com TSB crítico (abaixo de -35), o composto deve refletir "FADIGA CRÍTICA + PROGRESSÃO RÁPIDA".
     */
    void deveDiferenciarFadigaCriticaNoStatusCompostoComRampRate() {
        var metaDados = PlanoMetaDados.builder()
                .diaPreferidoLongo(DiaSemana.DOMINGO)
                .tsbAtual(-40.0)
                .ctlAtual(50.0)
                .rampRateAtual(11.0)
                .diasConsecutivosTreino(0)
                .semanasProgressaoContinua(0)
                .build();

        var resultado = service.analisarMetricas(metaDados);
        assertEquals("FADIGA CRÍTICA + PROGRESSÃO RÁPIDA", resultado.statusGeral());
    }

    @Test
    /**
     * Documenta o segundo ponto do ISSUE-01:
     * mesmo com sobrecarga + ramp rate crítico, se o TSB estiver na faixa de FADIGA ALTA (-35 < TSB <= -30),
     * o status composto deve ser "FADIGA ALTA + PROGRESSÃO RÁPIDA" (e não "FADIGA CRÍTICA + ...").
     */
    void deveDiferenciarFadigaAltaNoStatusCompostoComRampRate() {
        var metaDados = PlanoMetaDados.builder()
                .diaPreferidoLongo(DiaSemana.DOMINGO)
                .tsbAtual(-32.0)
                .ctlAtual(50.0)
                .rampRateAtual(11.0)
                .diasConsecutivosTreino(0)
                .semanasProgressaoContinua(0)
                .build();

        var resultado = service.analisarMetricas(metaDados);
        assertEquals("FADIGA ALTA + PROGRESSÃO RÁPIDA", resultado.statusGeral());
    }
}
