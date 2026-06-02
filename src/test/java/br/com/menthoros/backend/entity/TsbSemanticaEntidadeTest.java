package br.com.menthoros.backend.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * TDD — Section 1+2: valida que MetricasDiarias e PlanoMetaDados possuem
 * os campos de semântica TSB início/fim de dia.
 *
 * <p>Fase RED: compilação falha pois os campos ainda não existem.
 * <p>Fase GREEN: campos adicionados nas entidades e nas migrations V30/V31.
 */
@DisplayName("Semântica TSB: campos início/fim de dia nas entidades")
class TsbSemanticaEntidadeTest {

    @Test
    @DisplayName("MetricasDiarias possui campos ctlInicioDia e tsbInicioDia")
    void MetricasDiarias_temCamposInicioDia() {
        MetricasDiarias m = new MetricasDiarias();
        m.setCtlInicioDia(70.0);
        m.setTsbInicioDia(5.0);

        assertNotNull(m.getCtlInicioDia());
        assertEquals(70.0, m.getCtlInicioDia());
        assertEquals(5.0, m.getTsbInicioDia());
    }

    @Test
    @DisplayName("MetricasDiarias possui todos os seis campos início/fim de dia")
    void MetricasDiarias_temTodosSeisCampos() {
        MetricasDiarias m = new MetricasDiarias();
        m.setCtlInicioDia(70.0);
        m.setAtlInicioDia(75.0);
        m.setTsbInicioDia(-5.0);
        m.setCtlFimDia(70.5);
        m.setAtlFimDia(74.8);
        m.setTsbFimDia(-4.3);

        assertEquals(70.0, m.getCtlInicioDia());
        assertEquals(75.0, m.getAtlInicioDia());
        assertEquals(-5.0, m.getTsbInicioDia());
        assertEquals(70.5, m.getCtlFimDia());
        assertEquals(74.8, m.getAtlFimDia());
        assertEquals(-4.3, m.getTsbFimDia());
    }

    @Test
    @DisplayName("PlanoMetaDados possui tsbProntidaoAtual")
    void PlanoMetaDados_temTsbProntidaoAtual() {
        PlanoMetaDados p = new PlanoMetaDados();
        p.setTsbProntidaoAtual(3.0);

        assertEquals(3.0, p.getTsbProntidaoAtual());
    }

    @Test
    @DisplayName("PlanoMetaDados possui tsbPosCargaAtual")
    void PlanoMetaDados_temTsbPosCargaAtual() {
        PlanoMetaDados p = new PlanoMetaDados();
        p.setTsbPosCargaAtual(-5.0);

        assertEquals(-5.0, p.getTsbPosCargaAtual());
    }
}
