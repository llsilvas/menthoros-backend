package br.com.menthoros.backend.dto.input;

import com.fasterxml.jackson.annotation.JsonInclude;
import br.com.menthoros.backend.enums.PlanoStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(description = "Dados de entrada para criação ou atualização de um plano semanal de treino")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlanoSemanalInputDto(
        @Schema(description = "ID do plano semanal (usado em atualizações)", example = "123e4567-e89b-12d3-a456-426614174001")
        UUID id,

        @Schema(description = "ID do atleta vinculado ao plano semanal", example = "123e4567-e89b-12d3-a456-426614174000", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        UUID atletaId,

        @Schema(description = "ID do plano de treino principal", example = "123e4567-e89b-12d3-a456-426614174002")
        UUID planoTreinoId,

        @Schema(description = "Data de início da semana", example = "2024-01-15", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        LocalDate semanaInicio,

        @Schema(description = "Data de fim da semana", example = "2024-01-21", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        LocalDate semanaFim,

        @Schema(description = "Volume planejado em quilômetros", example = "50.0")
        double volumePlanejadoKm,

        @Schema(description = "Volume realizado em quilômetros", example = "48.5")
        double volumeRealizadoKm,

        @Schema(description = "Volume alvo em quilômetros", example = "52.0")
        double volumeAlvoKm,

        @Schema(description = "Training Stress Balance (TSB) no início da semana", example = "10.5")
        Double tsbInicio,

        @Schema(description = "Training Stress Balance (TSB) no fim da semana", example = "8.2")
        Double tsbFim,

        @Schema(description = "Status do plano semanal", example = "EM_PROGRESSO", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        PlanoStatus status,

        @Schema(description = "Observações sobre a semana de treino", example = "Semana de regeneração")
        String observacoes,

        @Schema(description = "Objetivo específico da semana", example = "Aumentar volume de base aeróbica")
        String objetivoSemanal,

        @Schema(description = "Lista de treinos planejados para a semana")
        List<TreinoPlanejadoInputDto> treinosPlanejados
) {}
