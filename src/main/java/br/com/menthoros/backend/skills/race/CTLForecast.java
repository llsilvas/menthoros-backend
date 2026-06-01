package br.com.menthoros.backend.skills.race;

import br.com.menthoros.backend.skills.race.enums.CtlTrend;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Previsão de CTL (Chronic Training Load) até o dia da prova.
 * Calculado pelo ConfidenceCalculator a partir do LoadProjection.
 */
@Schema(description = "Previsão de CTL até o dia da prova")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CTLForecast(

        @Schema(description = "CTL atual no momento da geração da projeção", example = "52.4")
        Double currentCtl,

        @Schema(description = "CTL projetado para o dia da prova", example = "61.0")
        Double projectedCtlRaceDay,

        @Schema(description = "Tendência de CTL calculada com base na variação por semana")
        CtlTrend ctlTrend,

        @Schema(description = "Semanas até o pico projetado de CTL", example = "4")
        Integer weeksToPeak
) {}
