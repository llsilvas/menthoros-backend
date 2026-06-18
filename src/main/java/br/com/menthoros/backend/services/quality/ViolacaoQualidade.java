package br.com.menthoros.backend.services.quality;

import br.com.menthoros.backend.services.prompt.constraint.ConstraintKey;

/**
 * Violação de uma {@link br.com.menthoros.backend.services.prompt.constraint.Constraint} detectada
 * pelo {@link PlanQualityChecker} no plano gerado pelo LLM.
 *
 * @param key      a {@link ConstraintKey} violada
 * @param mensagem descrição legível da violação (qual treino/etapa e por quê)
 */
public record ViolacaoQualidade(ConstraintKey key, String mensagem) {}
