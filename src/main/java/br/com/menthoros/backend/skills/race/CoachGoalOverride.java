package br.com.menthoros.backend.skills.race;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Meta de tempo definida pelo coach para uma distância específica.
 * Quando presente, ativa o cálculo de GoalGapAnalysis no output (D8).
 * Não altera os cálculos das 3 camadas determinísticas.
 */
@Schema(description = "Meta de tempo do coach para ativar GoalGapAnalysis")
public record CoachGoalOverride(

        @NotNull
        @Positive
        @Schema(description = "Tempo-meta em segundos", example = "6300")
        Long goalTimeSeconds,

        @NotNull
        @Positive
        @Schema(description = "Distância-alvo em metros à qual a meta se aplica", example = "21097")
        Integer targetDistanceM
) {}
