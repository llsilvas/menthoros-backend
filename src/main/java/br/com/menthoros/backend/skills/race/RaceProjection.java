package br.com.menthoros.backend.skills.race;

import br.com.menthoros.backend.skills.race.enums.ProjectionConfidence;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Projeção de tempo para uma distância-alvo específica.
 * Produzida pelo RaceProjectionAssembler após as 3 camadas de cálculo.
 */
@Schema(description = "Projeção de tempo para uma distância-alvo")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RaceProjection(

        @Schema(description = "Distância-alvo em metros", example = "21097")
        Integer distanceM,

        @Schema(description = "Tempo projetado ajustado em segundos", example = "6150")
        Long projectedTimeSeconds,

        @Schema(description = "Pace projetado em segundos por km", example = "291")
        Integer projectedPaceSecPerKm,

        @Schema(description = "Tempo otimista (projetado × 0.97) em segundos", example = "5966")
        Long timeRangeOptimisticSec,

        @Schema(description = "Tempo conservador (projetado × 1.03) em segundos", example = "6335")
        Long timeRangeConservativeSec,

        @Schema(description = "Nível de confiança da projeção")
        ProjectionConfidence confidence,

        @Schema(description = "Indica se o tempo projetado é melhor que o PR histórico nesta distância", example = "true")
        Boolean prPotential
) {}
