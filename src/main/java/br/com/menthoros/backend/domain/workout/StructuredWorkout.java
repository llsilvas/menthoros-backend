package br.com.menthoros.backend.domain.workout;

import java.time.LocalDate;
import java.util.List;

/**
 * Treino estruturado do Intervals.icu, mapeado para o modelo canônico de domínio.
 * namePrefix é opcional (null por default) — o adapter pré-concatena ao name quando presente.
 */
public record StructuredWorkout(
        String externalId,
        String name,
        String namePrefix,
        LocalDate scheduledDate,
        String description,
        List<WorkoutStep> steps
) {}
