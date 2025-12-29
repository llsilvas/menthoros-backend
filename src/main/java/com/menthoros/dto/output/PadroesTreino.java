package com.menthoros.dto.output;

/**
 * DTO contendo padrões de frequência e descanso do atleta.
 *
 * <p>Usado pelo {@link com.menthoros.services.TsbService} para identificar
 * sequências de treinos consecutivos e necessidade de descanso.
 *
 * <p><b>Fundamento:</b> Dias consecutivos de treino acima de 5-6 dias aumentam
 * significativamente o risco de overtraining e lesões. Monitorar esses padrões
 * permite ajustar o plano para incluir descanso obrigatório.
 *
 * @param diasConsecutivos Número de dias consecutivos com treino (até hoje)
 * @param diasDesdeDescanso Número de dias desde o último dia sem treino
 */
public record PadroesTreino(
        Integer diasConsecutivos,
        Integer diasDesdeDescanso
) {
}
