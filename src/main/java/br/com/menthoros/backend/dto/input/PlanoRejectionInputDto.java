package br.com.menthoros.backend.dto.input;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Dados de entrada para rejeitar um plano semanal")
public record PlanoRejectionInputDto(

        @Schema(description = "Motivo da rejeição do plano", example = "Volume excessivo para a fase atual do atleta")
        @NotBlank(message = "Motivo é obrigatório")
        @Size(max = 1000, message = "Motivo não pode exceder 1000 caracteres")
        String motivo

) {}
