package br.com.menthoros.backend.enums;

/**
 * Ciclo de vida de uma {@code SugestaoCoach}.
 *
 * <p>Transições válidas: PENDING → APPROVED ou PENDING → REJECTED.
 * Transições inversas lançam {@code DomainRuleViolationException}.
 * Re-aprovar/re-rejeitar no mesmo estado é no-op (idempotente).
 */
public enum StatusSugestao {
    PENDING,
    APPROVED,
    REJECTED
}
