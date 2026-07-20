package br.com.menthoros.backend.domain.planner;

/**
 * Fase de periodizacao decidida pelo {@link PlannerEngine}.
 *
 * <p>{@code CALIBRATION} e reservada: nenhuma logica desta change a emite — quem decide
 * quando reporta-la e a change {@code athlete-onboarding-baseline} (ver design.md Decisao 2).
 */
public enum TrainingPhase {
    BASE,
    BUILD,
    CALIBRATION,
    PEAK,
    TAPER,
    RACE_WEEK,
    RECOVERY,
    RETURN_TO_TRAINING,
    POST_RACE
}
