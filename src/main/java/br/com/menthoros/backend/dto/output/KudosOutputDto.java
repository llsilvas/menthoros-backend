package br.com.menthoros.backend.dto.output;

import br.com.menthoros.backend.enums.MotivoKudos;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Kudo registrado pelo coach para o atleta")
public record KudosOutputDto(

        @Schema(description = "ID do kudo")
        UUID id,

        @Schema(description = "ID do atleta reconhecido")
        UUID atletaId,

        @Schema(description = "ID do coach que registrou o kudo")
        UUID coachId,

        @Schema(description = "Motivo do reconhecimento", example = "CONSISTENCIA")
        MotivoKudos motivo,

        @Schema(description = "Momento em que o kudo foi registrado")
        Instant createdAt
) {}
