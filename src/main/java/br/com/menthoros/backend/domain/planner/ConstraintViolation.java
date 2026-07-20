package br.com.menthoros.backend.domain.planner;

/**
 * Violacao detectada pelo {@code ConstraintValidator}. Mesma forma de {@code PlannerViolation}
 * (key + mensagem) para consistencia — comparacao tipada em vez de substring matching.
 */
public record ConstraintViolation(ConstraintViolationKey key, String mensagem) {
}
