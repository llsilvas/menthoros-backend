package br.com.menthoros.backend.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

@Schema(description = "Status público da aplicação: nome, versão e horário do servidor")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StatusOutputDto(
        @Schema(description = "Nome da aplicação", example = "menthoros")
        String application,

        @Schema(description = "Versão da aplicação", example = "1.0.0")
        String version,

        @Schema(description = "Horário do servidor no momento da requisição", example = "2026-06-13T18:30:00-03:00")
        OffsetDateTime timestamp) {
}
