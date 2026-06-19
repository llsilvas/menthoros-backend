package br.com.menthoros.backend.dto.output;

import br.com.menthoros.backend.enums.MotivoAtencao;
import br.com.menthoros.backend.enums.Severidade;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Item da fila de atenção do treinador: um atleta que exige ação, com motivo, severidade,
 * ação sugerida e evidências que justificam a priorização.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Item priorizado da fila de atenção do treinador")
public record CoachAttentionItemOutputDto(

        @Schema(description = "ID do atleta")
        UUID atletaId,

        @Schema(description = "Nome do atleta", example = "Ana Silva")
        String athleteName,

        @Schema(description = "Severidade do item", example = "ALTA")
        Severidade severity,

        @Schema(description = "Score determinístico para ordenação dentro da mesma severidade", example = "240")
        int priorityScore,

        @Schema(description = "Motivo principal da priorização", example = "FADIGA")
        MotivoAtencao primaryReason,

        @Schema(description = "Ação sugerida (template determinístico por motivo)")
        String suggestedAction,

        @Schema(description = "Momento da geração da fila")
        Instant generatedAt,

        @Schema(description = "Evidências resumidas que justificam a priorização")
        List<Evidencia> evidence
) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Evidência de um sinal de atenção")
    public record Evidencia(

            @Schema(description = "Rótulo da evidência", example = "TSB")
            String label,

            @Schema(description = "Valor da evidência", example = "-22.4 (Fadiga excessiva)")
            String value
    ) {}
}
