package br.com.menthoros.backend.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.FonteDados;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Dados de saída dos metadados de planejamento")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlanoMetaDadosOutputDto(
        @Schema(description = "Volume semanal anterior em quilômetros", example = "45.5")
        Double volumeSemanalAnterior,

        @Schema(description = "Training Stress Balance (TSB) inicial", example = "12")
        Integer tsbInicial,

        @Schema(description = "Dia preferido para treino longo", example = "DOMINGO")
        DiaSemana diaPreferidoLongo,

        @Schema(description = "Fonte dos dados", example = "GARMIN")
        FonteDados fonteDados,

        @Schema(description = "Data e hora de criação", example = "2024-01-15T10:30:00")
        LocalDateTime dataCriacao
) {}
