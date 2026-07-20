package br.com.menthoros.backend.domain.planner;

/**
 * Modo de revisao do coach derivado da faixa de confianca (design.md Decisao 2, populado por
 * {@code athlete-onboarding-baseline}).
 */
public enum ReviewMode {
    EXCEPTION_ONLY,
    MANDATORY_NON_BLOCKING,
    MANDATORY_BLOCKING
}
