package com.menthoros.dto.input;

import com.menthoros.enums.PlanoStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Dados de entrada para criação de um plano de treino")
public record PlanoTreinoInputDto(
        @Schema(description = "Nome do plano de treino", example = "Preparação Maratona SP 2024", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Nome do plano é obrigatório")
        String nome,

        @Schema(description = "Descrição detalhada do plano", example = "Plano de 12 semanas focado em resistência aeróbica")
        String descricao,

        @Schema(description = "Data de início do plano", example = "2024-01-15", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Data de início é obrigatória")
        LocalDate dataInicio,

        @Schema(description = "Data da prova alvo", example = "2024-04-15")
        LocalDate dataProva,

        @Schema(description = "Objetivo do plano de treino", example = "Completar maratona sub 4h")
        String objetivo,

        @Schema(description = "Status atual do plano", example = "ATIVO", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Status é obrigatório")
        PlanoStatus status,

        @Schema(description = "ID do atleta vinculado ao plano", example = "123e4567-e89b-12d3-a456-426614174000", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "ID do atleta é obrigatório")
        UUID atletaId
) {}
