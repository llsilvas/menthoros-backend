package br.com.menthoros.backend.domain.planner;

/**
 * Faixa de risco fisiologico por TSB (design.md Decisao 15): SAFE (TSB > -10),
 * WARNING (-10 a -30), HIGH_RISK (< -30, forca {@code requiresCoachReview}).
 */
public enum InjuryRiskLevel {
    SAFE,
    WARNING,
    HIGH_RISK
}
