package com.menthoros.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.menthoros.entity.PlanoSemanal;
import com.menthoros.enums.PlanoStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(description = "Dados de saída de um plano de treino")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlanoTreinoOutputDto(
        @Schema(description = "Identificador único do plano", example = "123e4567-e89b-12d3-a456-426614174002")
        UUID id,

        @Schema(description = "Nome do plano de treino", example = "Preparação Maratona SP 2024")
        String nome,

        @Schema(description = "Descrição detalhada do plano", example = "Plano de 12 semanas focado em resistência aeróbica")
        String descricao,

        @Schema(description = "Data de início do plano", example = "2024-01-15")
        LocalDate dataInicio,

        @Schema(description = "Data da prova alvo", example = "2024-04-15")
        LocalDate dataProva,

        @Schema(description = "Objetivo do plano", example = "Completar maratona sub 4h")
        String objetivo,

        @Schema(description = "Status atual do plano", example = "ATIVO")
        PlanoStatus status,

        @Schema(description = "Lista de planos semanais do plano de treino")
        List<PlanoSemanalOutputDto> planoSemanalList
) {}
