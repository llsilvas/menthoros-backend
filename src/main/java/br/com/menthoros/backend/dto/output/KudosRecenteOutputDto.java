package br.com.menthoros.backend.dto.output;

import br.com.menthoros.backend.enums.MotivoKudos;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Kudo recebido pelo atleta, para exibição na Home")
public record KudosRecenteOutputDto(

        @Schema(description = "ID do kudo")
        UUID id,

        @Schema(description = "Motivo do reconhecimento", example = "CONSISTENCIA")
        MotivoKudos motivo,

        @Schema(description = "Momento em que o kudo foi registrado")
        Instant createdAt
) {}
