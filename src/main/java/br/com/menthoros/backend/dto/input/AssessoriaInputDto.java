package br.com.menthoros.backend.dto.input;

import br.com.menthoros.backend.enums.PlanoAssessoria;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "Dados de entrada para cadastro de uma assessoria (tenant)")
public record AssessoriaInputDto(

        @Schema(description = "Nome da assessoria", example = "Corridas Serra", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Nome é obrigatório")
        @Size(max = 200, message = "Nome deve ter no máximo 200 caracteres")
        String nome,

        @Schema(description = "Domínio único da assessoria", example = "corridasserra", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Domínio é obrigatório")
        @Size(max = 100, message = "Domínio deve ter no máximo 100 caracteres")
        String dominio,

        @Schema(description = "Plano contratado", example = "PRO", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Plano é obrigatório")
        PlanoAssessoria plano,

        @Schema(description = "E-mail de contato da assessoria", example = "contato@corridasserra.com")
        @Size(max = 100, message = "E-mail de contato deve ter no máximo 100 caracteres")
        String emailContato,

        @Schema(description = "Limite máximo de atletas", example = "100")
        @Positive(message = "Limite de atletas deve ser positivo")
        Integer maxAtletas,

        @Schema(description = "Limite máximo de técnicos", example = "10")
        @Positive(message = "Limite de técnicos deve ser positivo")
        Integer maxTecnicos
) {
}
