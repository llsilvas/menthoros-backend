package br.com.menthoros.backend.domain.planner;

/**
 * Saida do {@code InjuryRiskEvaluator} (design.md Decisao 15). {@code reason} carrega o
 * motivo textual quando {@code requiresCoachReview} e verdadeiro (ex.: TSB baixo, lesao ativa).
 */
public record InjuryRiskAssessment(
        InjuryRiskLevel level,
        boolean requiresCoachReview,
        String reason
) {
}
