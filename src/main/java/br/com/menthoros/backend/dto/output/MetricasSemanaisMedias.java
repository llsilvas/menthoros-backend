package br.com.menthoros.backend.dto.output;

import java.math.BigDecimal;

/**
 * DTO contendo médias semanais calculadas a partir do histórico de treinos realizados.
 *
 * <p>Usado pelo {@link br.com.menthoros.services.TsbService} para agregar métricas de
 * performance das últimas N semanas e fornecer contexto para geração de planos.
 *
 * @param volumeMedio Volume semanal médio em km (últimas N semanas)
 * @param tssMedio TSS semanal médio (últimas N semanas)
 * @param treinosPorSemanaMedio Número médio de treinos por semana
 */
public record MetricasSemanaisMedias(
        BigDecimal volumeMedio,
        Integer tssMedio,
        Double treinosPorSemanaMedio
) {
}
