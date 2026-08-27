package br.com.menthoros.backend.dto.input;

import br.com.menthoros.backend.enums.Sensacao;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * "Como foi?" pós-treino. RPE é obrigatório (é o mesmo campo de sempre, `percepcaoEsforco`);
 * sensações e comentário são opcionais. Completude é o carimbo — não a presença de cada campo.
 */
@Schema(description = "Feedback do atleta sobre um treino realizado")
public record FeedbackTreinoInputDto(

        @Schema(description = "Percepção de esforço (1-10)", example = "6", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "percepcaoEsforco é obrigatório")
        @Min(value = 1, message = "percepcaoEsforco deve ser entre 1 e 10")
        @Max(value = 10, message = "percepcaoEsforco deve ser entre 1 e 10")
        Integer percepcaoEsforco,

        @Schema(description = "Sensações do treino; lista fechada", example = "[\"PERNAS_PESADAS\"]")
        Set<Sensacao> sensacoes,

        @Schema(description = "Comentário livre sobre o treino", example = "Últimos 2km foram difíceis")
        @Size(max = 1000, message = "comentario deve ter no máximo 1000 caracteres")
        String comentario
) {}
