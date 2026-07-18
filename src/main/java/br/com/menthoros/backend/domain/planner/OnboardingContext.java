package br.com.menthoros.backend.domain.planner;

/**
 * Enriquecimento opcional de {@code DadosPlanoDto}, composicao (nao substituicao — design.md
 * Decisao 2). Atletas legados (pre-onboarding) continuam elegiveis ao planner em modo
 * {@code LEGACY_CONTEXT}, sem {@code OnboardingContext} (tratado como {@code Optional} no
 * {@link PlannerInputSnapshot}).
 */
public record OnboardingContext(
        AthleteBaseline baseline,
        double confidenceScore,
        PlanningPolicy planningPolicy,
        AthleteConstraints constraints
) {
}
