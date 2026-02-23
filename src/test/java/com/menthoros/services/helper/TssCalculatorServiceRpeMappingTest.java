package com.menthoros.services.helper;

import com.menthoros.entity.TreinoRealizado;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TssCalculatorServiceRpeMappingTest {

    private final TssCalculatorService service = new TssCalculatorService();

    @Test
    /**
     * Documenta o bug do ISSUE-02 no ponto fisiologicamente mais importante:
     * RPE 8 (limiar) deve mapear para IF ~ 1.0, o que implica ~100 TSS por hora.
     *
     * Esperado: ~100 TSS (1h)
     * Comportamento atual (antes do fix): subestima e retorna ~67 TSS (IF ≈ 0.82).
     */
    void deveCalcularCemTssPorHoraParaRpeOito() {
        var treino = new TreinoRealizado();
        treino.setDuracaoMin(Duration.ofMinutes(60));
        treino.setPercepcaoEsforco(8);

        int tss = service.calcularTss(treino);
        assertEquals(100, tss);
    }

    @Test
    /**
     * Valida um esforço moderado (RPE 5) com mapeamento fisiológico:
     * deve resultar em ~50-55 TSS por hora (IF ≈ 0.73).
     *
     * Esperado: 54 TSS (1h)
     * Comportamento atual (antes do fix): subestima e retorna ~30 TSS (IF ≈ 0.55).
     */
    void deveCalcularCinquentaEQuatroTssPorHoraParaRpeCinco() {
        var treino = new TreinoRealizado();
        treino.setDuracaoMin(Duration.ofMinutes(60));
        treino.setPercepcaoEsforco(5);

        int tss = service.calcularTss(treino);
        assertEquals(54, tss);
    }

    @Test
    /**
     * Valida o teto do mapeamento proposto (RPE 10):
     * deve permitir IF acima de 1.0 (p.ex. ~1.25) para esforços máximos, elevando o TSS/h.
     *
     * Esperado: 156 TSS (1h) para IF 1.25 (1.25² × 100 = 156.25)
     * Comportamento atual (antes do fix): trava em IF=1.0 e retorna 100 TSS.
     */
    void deveCalcularCentoECinquentaESeisTssPorHoraParaRpeDez() {
        var treino = new TreinoRealizado();
        treino.setDuracaoMin(Duration.ofMinutes(60));
        treino.setPercepcaoEsforco(10);

        int tss = service.calcularTss(treino);
        assertEquals(156, tss);
    }
}

