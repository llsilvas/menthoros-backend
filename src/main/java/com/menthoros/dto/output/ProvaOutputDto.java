package com.menthoros.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.menthoros.enums.ProvaStatus;
import com.menthoros.enums.TipoProva;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Dados de saída de uma prova")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProvaOutputDto(
        @Schema(description = "Identificador único da prova", example = "123e4567-e89b-12d3-a456-426614174005")
        UUID id,

        @Schema(description = "Nome da prova", example = "Maratona Internacional de São Paulo")
        String nomeProva,

        @Schema(description = "Data da prova", example = "2024-06-15")
        LocalDate dataProva,

        @Schema(description = "Distância em quilômetros", example = "42.195")
        Double distanciaKm,

        @Schema(description = "Tipo da prova", example = "MARATONA")
        TipoProva tipoProva,

        @Schema(description = "Status da prova", example = "PLANEJADA")
        ProvaStatus statusProva,

        @Schema(description = "Indica se é a prova alvo principal", example = "true")
        boolean provaAlvo,

        @Schema(description = "Objetivo na prova", example = "Completar em menos de 4 horas")
        String objetivo
) {}
