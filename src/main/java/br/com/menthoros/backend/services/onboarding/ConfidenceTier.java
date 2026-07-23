package br.com.menthoros.backend.services.onboarding;

/**
 * Faixa de confianca (design.md Decisao 3/4, athlete-onboarding-baseline):
 * A (&gt;= 75, {@code EXCEPTION_ONLY}), B (45-74, {@code MANDATORY_NON_BLOCKING}),
 * C (&lt; 45, {@code MANDATORY_BLOCKING}).
 */
public enum ConfidenceTier {
    A,
    B,
    C
}
