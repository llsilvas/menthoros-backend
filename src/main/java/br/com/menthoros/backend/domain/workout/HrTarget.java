package br.com.menthoros.backend.domain.workout;

/**
 * Alvo de frequência cardíaca para uma etapa de treino.
 * Suporta três unidades: BPM (batidas por minuto), PERCENT (percentual de FC máxima), ZONE (zona de treinamento).
 * Para ZONE, start e end são ambos o número da zona (e.g., zona 2 = ZONE.start=2, end=2).
 */
public record HrTarget(HrTarget.Unidade unidade, Integer start, Integer end) {
    public enum Unidade {
        BPM, PERCENT, ZONE
    }
}
