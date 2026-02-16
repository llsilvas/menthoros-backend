package com.menthoros.dto.output;

import com.menthoros.dto.AlertaMetricas;

import java.util.List;

/**
 * Resultado da análise de métricas de um atleta.
 *
 * <p>Produzido pelo {@link com.menthoros.services.impl.MetricasAlertaService}
 * e aplicado ao {@link com.menthoros.entity.PlanoMetaDados} via {@code aplicarAnalise()}.
 */
public record ResultadoAnalise(
        String statusGeral,
        String recomendacaoTreino,
        String mensagemAlerta,
        boolean alertaSobrecarga,
        boolean alertaRampAlto,
        boolean alertaDiasConsecutivos,
        boolean alertaNecessitaDescanso,
        List<AlertaMetricas> alertas
) {}
