package br.com.menthoros.backend.skills.analysis;

/**
 * Payload do resultado produzido por {@link LongRunAnalysisSkill}.
 *
 * @param driftFc           percentual de drift de frequência cardíaca entre o primeiro e o último
 *                          terço do treino (sinal de fadiga aeróbia); 0.0 quando não calculado
 * @param progressaoCorreta {@code true} se o pace foi mantido ou melhorado no último terço do treino
 * @param qualidadeAerobia  classificação da qualidade aeróbia: ALTA, MODERADA ou BAIXA
 * @param interpretacao     texto interpretativo gerado pela skill
 */
public record LongRunAnalysisPayload(
        double driftFc,
        boolean progressaoCorreta,
        String qualidadeAerobia,
        String interpretacao
) {}
