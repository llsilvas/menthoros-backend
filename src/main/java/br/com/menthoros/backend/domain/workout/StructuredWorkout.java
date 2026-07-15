package br.com.menthoros.backend.domain.workout;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

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
) {

    /**
     * Prefixo canônico do {@code external_id} usado nos eventos que o Menthoros cria no
     * intervals.icu — único ponto de verdade, reutilizado pelo converter (montagem do id), pelo
     * listener (set de reconciliação de órfãos) e pelo adapter (filtro de eventos próprios).
     */
    public static final String PREFIXO_EXTERNAL_ID = "menthoros-";

    /**
     * Monta o {@code external_id} canônico e determinístico de um treino planejado — mesmo valor
     * sempre para o mesmo treino, independente de quantas vezes for sincronizado.
     *
     * @param treinoId id do treino planejado
     * @return o external_id no formato {@code "menthoros-<treinoId>"}
     */
    public static String externalIdCanonico(UUID treinoId) {
        if (treinoId == null) {
            throw new IllegalArgumentException("treinoId não pode ser nulo");
        }
        return PREFIXO_EXTERNAL_ID + treinoId;
    }
}
