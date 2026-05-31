package br.com.menthoros.backend.skills.race;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * Resultado do ajuste por periodização e TSB (Camada 3).
 * Produzido por PeriodizationAdjuster e consumido pelo RaceProjectionAssembler.
 */
@Schema(description = "Tempos ajustados por fase de periodização e estado de TSB (Camada 3)")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdjustmentResult(

        @Schema(description = "Fator multiplicativo aplicado. < 1.0 = melhora; > 1.0 = penalidade.", example = "0.975")
        double adjustmentFactor,

        @Schema(description = "Chave que identifica o cenário de ajuste aplicado.",
                example = "TAPER_OPTIMAL",
                allowableValues = {"TAPER_OPTIMAL", "BUILD_PEAK", "ESPECIFICO_FRESH",
                        "BASE_CONSERVATIVE", "FATIGUED", "OVERTAPERED"})
        String adjustmentRationaleKey,

        @Schema(description = "Tempos finais ajustados em segundos por distância-alvo (metros).")
        Map<Integer, Long> adjustedTimes
) {}
