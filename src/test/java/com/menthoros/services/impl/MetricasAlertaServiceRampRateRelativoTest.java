package com.menthoros.services.impl;

import com.menthoros.entity.PlanoMetaDados;
import com.menthoros.enums.DiaSemana;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MetricasAlertaServiceRampRateRelativoTest {

    private final MetricasAlertaService service = new MetricasAlertaService();

    @Test
    /**
     * Documenta o ISSUE-05 na camada de alerta:
     * uma progressão moderada em atleta avançado (ex: CTL 110 -> 120, +10 pts)
     * não deveria acionar alerta ALTO/MODERADO quando avaliada de forma relativa (~9.1%).
     *
     * Antes do fix (threshold absoluto): 10.0 pts/sem aciona alerta crítico.
     *
     * Esperado (threshold relativo): nenhum alerta de ramp rate.
     * Comportamento atual (antes do fix): alerta RAMP_RATE_ALTO.
     */
    void naoDeveAlertarRampRateQuandoVariacaoRelativaEhBaixa() {
        var metaDados = PlanoMetaDados.builder()
                .diaPreferidoLongo(DiaSemana.DOMINGO)
                .ctlAtual(120.0)
                .tsbAtual(10.0)
                .rampRateAtual(10.0)
                .diasConsecutivosTreino(0)
                .semanasProgressaoContinua(0)
                .build();

        var resultado = service.analisarMetricas(metaDados);
        long rampAlerts = resultado.alertas().stream()
                .filter(a -> "RAMP_RATE_ALTO".equals(a.codigo()) || "RAMP_RATE_MODERADO".equals(a.codigo()))
                .count();

        assertEquals(0, rampAlerts);
    }

    @Test
    /**
     * Complementa o ISSUE-05:
     * um ramp-up de iniciante (ex: CTL 14 -> 20, +6 pts) deve ser detectado,
     * pois a variação relativa é alta (~42.9%).
     */
    void deveAlertarRampRateQuandoVariacaoRelativaEhAlta() {
        var metaDados = PlanoMetaDados.builder()
                .diaPreferidoLongo(DiaSemana.DOMINGO)
                .ctlAtual(20.0)
                .tsbAtual(0.0)
                .rampRateAtual(6.0)
                .diasConsecutivosTreino(0)
                .semanasProgressaoContinua(0)
                .build();

        var resultado = service.analisarMetricas(metaDados);
        long rampAlerts = resultado.alertas().stream()
                .filter(a -> "RAMP_RATE_ALTO".equals(a.codigo()) || "RAMP_RATE_MODERADO".equals(a.codigo()))
                .count();

        assertEquals(1, rampAlerts);
    }
}
