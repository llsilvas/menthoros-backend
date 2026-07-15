package br.com.menthoros.backend.domain.workout;

import java.util.List;

/**
 * Uma etapa/passo de um treino estruturado.
 * Pode ser simples (com duração/distância e alvos) ou um bloco (grupo de repetições de sub-etapas).
 */
public record WorkoutStep(
        String text,
        Integer durationSeconds,
        Integer distanceMeters,
        PaceTarget pace,
        HrTarget hr,
        Integer reps,
        List<WorkoutStep> steps
) {
    /**
     * Factory para etapa simples: sem repetições nem sub-etapas.
     */
    public static WorkoutStep simples(String text, Integer durationSeconds, Integer distanceMeters,
                                      PaceTarget pace, HrTarget hr) {
        return new WorkoutStep(text, durationSeconds, distanceMeters, pace, hr, null, null);
    }

    /**
     * Factory para bloco: grupo de repetições de sub-etapas.
     */
    public static WorkoutStep bloco(String text, int reps, List<WorkoutStep> steps) {
        return new WorkoutStep(text, null, null, null, null, reps, steps);
    }
}
