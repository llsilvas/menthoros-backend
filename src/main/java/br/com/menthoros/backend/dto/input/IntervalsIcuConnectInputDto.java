package br.com.menthoros.backend.dto.input;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Credencial pessoal do intervals.icu para conexão")
public record IntervalsIcuConnectInputDto(
        @Schema(description = "API key gerada em intervals.icu -> Settings -> Developer")
        @NotBlank(message = "apiKey é obrigatória")
        @Size(max = 128)
        String apiKey
) {}
