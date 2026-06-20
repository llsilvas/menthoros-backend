package br.com.menthoros.backend.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Aderência semanal ao plano de treino")
public record AderenciasSemanalDto(

        @Schema(description = "Data de início da semana (segunda-feira ISO)", example = "2026-06-02")
        LocalDate semanaInicio,

        @Schema(description = "Total de treinos planejados na semana", example = "5")
        int totalPlanejado,

        @Schema(description = "Total de treinos realizados na semana", example = "4")
        int totalRealizado,

        @Schema(description = "Percentual de aderência (0–100)", example = "80")
        int percentual
) {}
