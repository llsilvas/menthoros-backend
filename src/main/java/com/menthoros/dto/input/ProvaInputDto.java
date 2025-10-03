package com.menthoros.dto.input;

import com.menthoros.enums.ProvaStatus;
import com.menthoros.enums.TipoProva;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Dados de entrada para cadastro ou atualização de uma prova")
public record ProvaInputDto(
        @Schema(description = "Nome da prova", example = "Maratona Internacional de São Paulo", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Nome da prova é obrigatório")
        String nomeProva,

        @Schema(description = "Data da realização da prova", example = "2024-06-15", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Data da prova é obrigatória")
        LocalDate dataProva,

        @Schema(description = "Distância da prova em quilômetros", example = "42.195", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Distância é obrigatória")
        @Positive(message = "Distância deve ser positiva")
        Double distanciaKm,

        @Schema(description = "Tipo da prova", example = "MARATONA", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Tipo da prova é obrigatório")
        TipoProva tipoProva,

        @Schema(description = "Status atual da prova", example = "PLANEJADA", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Status da prova é obrigatório")
        ProvaStatus statusProva,

        @Schema(description = "Indica se esta é a prova alvo principal do atleta", example = "true")
        boolean provaAlvo,

        @Schema(description = "Objetivo do atleta na prova", example = "Completar em menos de 4 horas")
        String objetivo,

        @Schema(description = "ID do atleta vinculado à prova", example = "123e4567-e89b-12d3-a456-426614174000", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "ID do atleta é obrigatório")
        UUID atletaId
) {}
