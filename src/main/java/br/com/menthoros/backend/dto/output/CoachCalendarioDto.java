package br.com.menthoros.backend.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Calendário semanal do coach: treinos planejados de todos os atletas do tenant na semana.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Calendário semanal agregado do coach")
public record CoachCalendarioDto(

        @Schema(description = "Início da semana (segunda)", example = "2026-06-15")
        LocalDate semanaInicio,

        @Schema(description = "Fim da semana (domingo)", example = "2026-06-21")
        LocalDate semanaFim,

        @Schema(description = "Treinos planejados da semana, de todos os atletas do tenant")
        List<TreinoAgendado> treinos
) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Treino planejado no calendário do coach")
    public record TreinoAgendado(

            @Schema(description = "ID do atleta")
            UUID atletaId,

            @Schema(description = "Nome do atleta", example = "Ana Silva")
            String nomeAtleta,

            @Schema(description = "Data do treino", example = "2026-06-17")
            LocalDate data,

            @Schema(description = "Tipo do treino", example = "INTERVALADO")
            String tipoTreino,

            @Schema(description = "Treino-chave da semana (intervalado/tiro/longo/tempo run)", example = "true")
            boolean isKeyWorkout,

            @Schema(description = "Há alerta para este treino (fonte: attention-queue; default false)", example = "false")
            boolean hasAlert,

            @Schema(description = "Há sugestão IA pendente (fonte: suggestion-inbox; default false)", example = "false")
            boolean hasPendingSuggestion
    ) {}
}
