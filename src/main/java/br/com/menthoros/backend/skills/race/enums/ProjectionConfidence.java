package br.com.menthoros.backend.skills.race.enums;

/**
 * Nível de confiança da projeção.
 * overall_confidence = min(confidence_layer1, confidence_riegel_calibration) — ver D2.
 */
public enum ProjectionConfidence {
    /** R² &lt; 0.4 ou &lt; 6 sessões, ou dados sem FC, ou Riegel não calibrado. */
    LOW,
    /** R² entre 0.4 e 0.7, ou Riegel com expoente padrão. */
    MEDIUM,
    /** R² ≥ 0.7 e Riegel calibrado com 2+ provas históricas. */
    HIGH
}
