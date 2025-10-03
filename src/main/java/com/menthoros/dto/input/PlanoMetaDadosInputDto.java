package com.menthoros.dto.input;

import com.menthoros.enums.DiaSemana;
import com.menthoros.enums.FonteDados;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Dados de entrada para metadados de planejamento de treino")
public record PlanoMetaDadosInputDto(
        @Schema(description = "Volume semanal anterior em quilômetros", example = "45.5")
        Double volumeSemanalAnterior,

        @Schema(description = "Training Stress Balance (TSB) inicial", example = "12")
        Integer tsbInicial,

        @Schema(description = "Dia preferido para treino longo", example = "DOMINGO")
        DiaSemana diaPreferidoLongo,

        @Schema(description = "Fonte dos dados utilizados", example = "GARMIN")
        FonteDados fonteDados,

        @Schema(description = "Data e hora de criação dos metadados", example = "2024-01-15T10:30:00")
        LocalDateTime dataCriacao
) {}
