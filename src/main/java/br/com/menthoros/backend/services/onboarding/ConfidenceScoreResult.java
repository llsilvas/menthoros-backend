package br.com.menthoros.backend.services.onboarding;

/**
 * Resultado do {@link ConfidenceScorer} — score 0-100 e tier (design.md
 * Decisao 3, athlete-onboarding-baseline). {@code tier} ja reflete o bonus
 * coach-como-proxy (Decisao 3), se aplicavel; {@code scoreBruto} preserva o
 * valor calculado ANTES do bonus, para auditoria/transparencia.
 */
public record ConfidenceScoreResult(
        int scoreBruto,
        ConfidenceTier tier,
        boolean bonusCoachAplicado
) {
}
