package br.com.menthoros.backend.dto.output;

import br.com.menthoros.backend.domain.planner.TrainingPhase;
import br.com.menthoros.backend.services.onboarding.CalibrationStage;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Status de calibração do atleta (task 6.0.4) para o CalibrationBanner do front")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CalibracaoStatusOutputDto(
        @Schema(description = "Fase de periodização — sempre CALIBRATION quando este DTO existe")
        TrainingPhase phase,

        @Schema(description = "Estágio interno da calibração", example = "OBSERVATION")
        CalibrationStage stage,

        @Schema(description = "Número da semana desde o início da calibração", example = "2")
        int weekNumber,

        @Schema(description = "Score de confiança bruto (0-100)", example = "40")
        int confidenceScore
) {
}
