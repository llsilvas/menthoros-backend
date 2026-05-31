package br.com.menthoros.backend.skills.race;

import br.com.menthoros.backend.skills.race.enums.ProjectionConfidence;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Resultado da regressão linear de pace normalizado (Camada 1).
 * Produzido por PaceRegressionCalculator e consumido por RiegelCalculator.
 */
@Schema(description = "Resultado da regressão de pace normalizado (Camada 1)")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RegressionResult(

        @Schema(description = "Inclinação da reta de regressão (seg/km por semana). Negativo = melhora de pace.")
        Double slope,

        @Schema(description = "Coeficiente de determinação R² da regressão (0.0–1.0). Null quando < 6 sessões.")
        Double rSquared,

        @Schema(description = "Pace projetado (seg/km) para a semana da prova, já normalizado por FC.")
        double projectedPaceAtRaceWeek,

        @Schema(description = "Confiança calculada pela Camada 1 com base em R² e número de sessões.")
        ProjectionConfidence confidenceLayer1,

        @Schema(description = "Número de sessões TEMPO_RUN e LONGO usadas na regressão.")
        int sessionsUsed,

        @Schema(description = "Quando true, a regressão usou fallback de pace plano (data_quality=SPARSE ou < 6 sessões).")
        boolean fallbackUsed,

        @Schema(description = "Premissa registrada quando dados de FC estão ausentes. Null quando FC disponível.")
        String hrMissingAssumption
) {}
