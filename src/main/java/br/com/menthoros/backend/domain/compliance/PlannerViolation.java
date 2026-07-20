package br.com.menthoros.backend.domain.compliance;

/**
 * Violacao detectada pelo {@code SkeletonComplianceChecker}. Mesma forma de
 * {@code ViolacaoQualidade} (key + mensagem) para consistencia de log/metrica
 * (design.md Decisao 4).
 */
public record PlannerViolation(PlannerViolationKey key, String mensagem) {
}
