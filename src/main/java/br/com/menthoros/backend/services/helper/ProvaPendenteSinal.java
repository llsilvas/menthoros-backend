package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.enums.MotivoRevisaoProva;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

/**
 * Prova pendente de ciência do coach, já reduzida aos valores que o
 * {@link CoachAttentionSignalEvaluator} precisa — o serviço faz o mapeamento a partir da entidade.
 */
public record ProvaPendenteSinal(
        String nome,
        LocalDate data,
        String distancia,
        int semanasFaltando,
        int semanasMinimas,
        boolean preparacaoCurta,
        MotivoRevisaoProva motivo,
        @Nullable String alvoAnteriorNome
) {
}
