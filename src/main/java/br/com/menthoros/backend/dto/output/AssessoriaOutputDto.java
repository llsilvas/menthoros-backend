package br.com.menthoros.backend.dto.output;

import br.com.menthoros.backend.enums.PlanoAssessoria;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Dados de saída de uma assessoria (tenant)")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AssessoriaOutputDto(

        @Schema(description = "Identificador único da assessoria")
        UUID id,

        @Schema(description = "Nome da assessoria", example = "Corridas Serra")
        String nome,

        @Schema(description = "Domínio único da assessoria", example = "corridasserra")
        String dominio,

        @Schema(description = "Plano contratado", example = "PRO")
        PlanoAssessoria plano,

        @Schema(description = "ID da Organization no Keycloak", example = "org-123")
        String keycloakOrganizationId,

        @Schema(description = "Limite máximo de atletas", example = "100")
        Integer maxAtletas,

        @Schema(description = "Limite máximo de técnicos", example = "10")
        Integer maxTecnicos,

        @Schema(description = "Indica se a assessoria está ativa", example = "true")
        boolean ativo,

        @Schema(description = "Data de criação", example = "2026-06-10T10:15:30")
        LocalDateTime createdAt
) {
}
