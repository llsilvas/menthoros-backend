package br.com.menthoros.backend.services.onboarding;

/**
 * Estagio interno de {@code TrainingPhase.CALIBRATION} (design.md Decisao 5,
 * athlete-onboarding-baseline) — NAO e um novo valor do enum de fase; o
 * {@code PlannerEngine} reporta {@code phase = CALIBRATION} ao restante do
 * sistema, mas usa este estagio internamente para decidir conservadorismo.
 */
public enum CalibrationStage {
    OBSERVATION,   // semana 1
    CALIBRATION,   // semana 2
    STABILIZATION  // semanas 3-4
}
