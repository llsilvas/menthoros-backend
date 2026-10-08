package br.com.menthoros.backend.enums;

/**
 * Veredito determinístico de aderência ao plano (sem LLM), calculado a partir de executado vs.
 * planejado por {@code WorkoutPlanVerdictCalculator} (add-athlete-workout-verdict-chip, D2).
 */
public enum WorkoutPlanVerdict {
    DENTRO_DO_PLANO,
    ABAIXO_DO_PLANO,
    ACIMA_DO_PLANO,
    ESFORCO_ACIMA_DO_ESPERADO
}
