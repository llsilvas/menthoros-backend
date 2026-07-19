package br.com.menthoros.backend.domain.planner;

/**
 * Identifica o tipo de violacao detectada pelo {@code ConstraintValidator}. Tipo proprio,
 * simetrico a {@code PlannerViolationKey} (domain/compliance) — mesma razao: nao ha chave
 * generica reaproveitavel para dia/max-sessoes/duracao/equipamento no legado.
 */
public enum ConstraintViolationKey {
    DIA_INDISPONIVEL,
    MAX_SESSOES_EXCEDIDO,
    DURACAO_MAXIMA_EXCEDIDA,
    EQUIPAMENTO_INDISPONIVEL
}
