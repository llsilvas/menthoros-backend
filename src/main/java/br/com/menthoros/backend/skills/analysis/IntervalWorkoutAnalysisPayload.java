package br.com.menthoros.backend.skills.analysis;

/**
 * Payload do resultado produzido por {@link IntervalWorkoutAnalysisSkill}.
 *
 * @param ifMedio           Intensity Factor médio calculado (fcMedia / fcLimiar)
 * @param qualidadeExecucao classificação da qualidade de execução:
 *                          EXCELENTE (IF >= 1.05), BOA (0.95 <= IF < 1.05),
 *                          REGULAR (0.85 <= IF < 0.95), FRACA (IF < 0.85)
 * @param etapasAnalisadas  {@code true} se o cálculo usou etapas individuais;
 *                          {@code false} se usou a FC global (fallback)
 * @param interpretacao     texto interpretativo gerado pela skill
 */
public record IntervalWorkoutAnalysisPayload(
        double ifMedio,
        String qualidadeExecucao,
        boolean etapasAnalisadas,
        String interpretacao
) {}
