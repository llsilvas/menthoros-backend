package br.com.menthoros.backend.dto.output;

import br.com.menthoros.backend.dto.AlertaMetricas;

import java.util.List;

/**
 * Resultado da análise de métricas de um atleta.
 *
 * <p>Produzido pelo {@link br.com.menthoros.services.impl.MetricasAlertaService}
 * e aplicado ao {@link br.com.menthoros.entity.PlanoMetaDados} via {@code aplicarAnalise()}.
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
