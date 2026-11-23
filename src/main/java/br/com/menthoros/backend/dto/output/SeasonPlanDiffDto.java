package br.com.menthoros.backend.dto.output;

import br.com.menthoros.backend.enums.WeekValueSource;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Um campo de uma semana que mudou do ajuste (rascunho) para a temporada ativa de que ele partiu
 * (add-season-plan-version-diff).
 */
@Schema(description = "Campo de uma semana que mudou no ajuste em relação à temporada ativa")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SeasonPlanDiffDto(
        @Schema(description = "Semana", example = "5") int weekIndex,
        @Schema(description = "Campo: phase, targetVolumeKm, targetTss, longRunKm, deload ou keySessions",
                example = "longRunKm") String field,
        @Schema(description = "Valor na temporada ativa; nulo se não havia", example = "18.0")
        @JsonInclude(JsonInclude.Include.ALWAYS) Object before,
        @Schema(description = "Valor no ajuste; nulo se foi limpo", example = "20.0")
        @JsonInclude(JsonInclude.Include.ALWAYS) Object after,
        @Schema(description = "Quem definiu os valores da semana no ajuste", example = "COACH") WeekValueSource source
) {
}
