package br.com.menthoros.backend.skills.race;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * Resultado da conversão de tempo por fórmula de Riegel (Camada 2).
 * Produzido por RiegelCalculator e consumido por PeriodizationAdjuster.
 */
@Schema(description = "Tempos base calculados por Riegel para cada distância-alvo (Camada 2)")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RiegelResult(

        @Schema(description = "Tempos base em segundos por distância-alvo (metros). Ainda sem ajuste de periodização.")
        Map<Integer, Long> baseTimesSec,

        @Schema(description = "Expoente de Riegel utilizado no cálculo.", example = "1.06")
        double exponentUsed,

        @Schema(description = "Indica se o expoente foi calibrado com histórico de provas do atleta.")
        boolean calibrated,

        @Schema(description = "Número de pares de provas usados na calibração. 0 quando não calibrado.")
        int calibrationSampleSize,

        @Schema(description = "Fonte do tempo âncora usado como ponto de partida da conversão.",
                example = "LAYER1_PACE", allowableValues = {"LAYER1_PACE", "BEST_RECENT_RACE", "TEMPO_ESTIMATED"})
        String anchorSource
) {}
