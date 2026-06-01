package br.com.menthoros.backend.skills.race;

import br.com.menthoros.backend.skills.race.enums.ProjectionConfidence;
import org.springframework.stereotype.Component;

/**
 * Calcula a confiança global da projeção como o mínimo entre Camada 1 e qualidade da calibração Riegel.
 *
 * Regra D2 do design.md:
 *   overall_confidence = min(confidence_layer1, calibration_quality_as_confidence)
 *   Riegel não calibrado reduz a confiança em 1 nível.
 *
 * Idempotent: YES — leitura pura, sem side effects.
 * Side Effects: NONE
 * Tenant-aware: NO
 */
@Component
public class ConfidenceCalculator {

    /**
     * Determina a confiança global combinando regressão e calibração de Riegel.
     *
     * @param regressionConfidence confiança calculada pela Camada 1
     * @param riegelCalibrated     se o expoente de Riegel foi calibrado com histórico real
     * @return menor valor entre os dois níveis de confiança
     */
    public ProjectionConfidence calculate(ProjectionConfidence regressionConfidence, boolean riegelCalibrated) {
        if (regressionConfidence == null) throw new IllegalArgumentException("regressionConfidence cannot be null");

        ProjectionConfidence riegelConfidence = riegelCalibrated
                ? ProjectionConfidence.HIGH
                : downgrade(regressionConfidence);

        return min(regressionConfidence, riegelConfidence);
    }

    private ProjectionConfidence downgrade(ProjectionConfidence confidence) {
        return switch (confidence) {
            case HIGH   -> ProjectionConfidence.MEDIUM;
            case MEDIUM -> ProjectionConfidence.LOW;
            case LOW    -> ProjectionConfidence.LOW;
        };
    }

    private ProjectionConfidence min(ProjectionConfidence a, ProjectionConfidence b) {
        return a.ordinal() <= b.ordinal() ? a : b;
    }
}
