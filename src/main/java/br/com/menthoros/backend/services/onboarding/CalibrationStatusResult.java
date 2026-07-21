package br.com.menthoros.backend.services.onboarding;

import br.com.menthoros.backend.domain.planner.TrainingPhase;

/**
 * Status de calibracao para o {@code CalibrationBanner} do front (task 6.0.4/8.2,
 * athlete-onboarding-baseline). {@code phase} e sempre {@link TrainingPhase#CALIBRATION} quando
 * este tipo existe — {@code OnboardingService.obterStatusCalibracao} retorna vazio quando o
 * atleta nao esta em calibracao.
 */
public record CalibrationStatusResult(
        TrainingPhase phase,
        CalibrationStage stage,
        int weekNumber,
        int confidenceScore
) {
}
