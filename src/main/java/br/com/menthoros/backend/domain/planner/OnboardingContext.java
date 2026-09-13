package br.com.menthoros.backend.domain.planner;

/**
 * Enriquecimento opcional de {@code DadosPlanoDto}, composicao (nao substituicao — design.md
 * Decisao 2). Atletas legados (pre-onboarding) continuam elegiveis ao planner em modo
 * {@code LEGACY_CONTEXT}, sem {@code OnboardingContext} (tratado como {@code Optional} no
 * {@link PlannerInputSnapshot}).
 *
 * <p>{@code calibrationStage} sinaliza o regime cold-start do modelo de carga (ADR-0012): presente
 * enquanto o atleta está em calibração, {@code null} quando graduou (ou nunca calibrou — tier A). O
 * {@code LoadTargetResolver} usa a presença para decidir rampa/cap; ausente cai no caminho normal (PMC).
 */
public record OnboardingContext(
        AthleteBaseline baseline,
        double confidenceScore,
        PlanningPolicy planningPolicy,
        AthleteConstraints constraints,
        CalibrationStage calibrationStage
) {
}
