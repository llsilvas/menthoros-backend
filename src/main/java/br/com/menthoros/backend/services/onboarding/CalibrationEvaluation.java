package br.com.menthoros.backend.services.onboarding;

import br.com.menthoros.backend.domain.planner.CalibrationStage;

/**
 * Resultado da avaliacao semanal de calibracao (design.md Decisao 5,
 * athlete-onboarding-baseline) — combina o re-baseline + re-score da semana
 * com a decisao de saida da fase {@code CALIBRATION}.
 */
public record CalibrationEvaluation(
        CalibrationStage stage,
        BaselineResult baseline,
        ConfidenceScoreResult confidenceScore,
        boolean elegivelParaSairDaCalibracao
) {
}
