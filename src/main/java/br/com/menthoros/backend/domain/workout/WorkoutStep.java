package br.com.menthoros.backend.domain.workout;

import java.util.List;

/**
 * Uma etapa/passo de um treino estruturado.
 * Pode ser simples (com duração/distância e meta de intensidade) ou um bloco (grupo de repetições
 * de sub-etapas).
 */
public record WorkoutStep(
        String text,
        Integer durationSeconds,
        Integer distanceMeters,
        IntensityTarget meta,
        Integer reps,
        List<WorkoutStep> steps,
        // Etapa de recuperação prescrita (trote entre tiros). "Sem meta" não basta para saber isso:
        // um tiro livre também não tem meta, e o canal só pode pular o que é descanso de fato.
        boolean recovery
) {
    /**
     * Factory para etapa simples: sem repetições nem sub-etapas.
     * Meta nula é normalizada para {@link IntensityTarget#SEM_OBJETIVO} — a ausência de meta é uma
     * escolha nomeada, e representá-la por {@code null} devolveria ao chamador a pergunta que este
     * tipo existe para responder.
     */
    public static WorkoutStep simples(String text, Integer durationSeconds, Integer distanceMeters,
                                      IntensityTarget meta) {
        return new WorkoutStep(text, durationSeconds, distanceMeters,
                meta != null ? meta : IntensityTarget.SEM_OBJETIVO, null, null, false);
    }

    /** Factory para bloco: grupo de repetições de sub-etapas. */
    public static WorkoutStep bloco(String text, int reps, List<WorkoutStep> steps) {
        return new WorkoutStep(text, null, null, IntensityTarget.SEM_OBJETIVO, reps, steps, false);
    }

    /** A mesma etapa marcada como recuperação prescrita. */
    public WorkoutStep asRecovery() {
        return new WorkoutStep(text, durationSeconds, distanceMeters, meta, reps, steps, true);
    }
}
