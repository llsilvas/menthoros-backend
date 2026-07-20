package br.com.menthoros.backend.domain.planner;

/**
 * Contrato minimo reservado para {@code athlete-onboarding-baseline} (design.md Decisao 2).
 */
public record PlanningPolicy(
        ReviewMode reviewMode,
        Double maxProgressionAllowed,
        boolean explanationRequired
) {
}
