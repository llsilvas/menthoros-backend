package br.com.menthoros.backend.dto.output;

/**
 * DTO contendo padrões de frequência e descanso do atleta.
 *
 * <p>Usado pelo {@link br.com.menthoros.services.TsbService} para identificar
 * sequências de treinos consecutivos e necessidade de descanso.
 *
 * <p><b>Fundamento:</b> Dias consecutivos de treino acima de 5-6 dias aumentam
 * significativamente o risco de overtraining e lesões. Monitorar esses padrões
 * permite ajustar o plano para incluir descanso obrigatório.
 *
 * @param diasConsecutivos Sequência atual de dias consecutivos com treino (terminando no treino mais recente)
 * @param diasDesdeDescanso Dias consecutivos com treino contados a partir de hoje (0 se houve descanso hoje)
 */
public record PadroesTreino(
        Integer diasConsecutivos,
        Integer diasDesdeDescanso
) {
}
