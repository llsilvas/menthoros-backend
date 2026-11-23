package br.com.menthoros.backend.dto.input;

import br.com.menthoros.backend.domain.planner.TrainingPhase;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Parâmetros do recálculo do ajuste da temporada (add-season-plan-version-diff).
 *
 * <p>Como no {@link SeasonWeekUpdateInputDto}, as anotações documentam o contrato no OpenAPI, mas a rota
 * não usa {@code @Valid}: o {@code SeasonPlanAdjustmentServiceImpl} valida e responde 422 com mensagem.
 */
@Schema(description = "Semanas por fase e volume de partida para regenerar as semanas livres do ajuste")
public record SeasonPlanRecalculateInputDto(
        @Schema(description = "Semanas de BASE, BUILD, PEAK e TAPER; a semana da prova é sempre uma e fica fora",
                example = "{\"BASE\": 3, \"BUILD\": 4, \"PEAK\": 2, \"TAPER\": 2}")
        @NotNull(message = "Informe as semanas por fase")
        Map<TrainingPhase, Integer> phaseWeeks,

        @Schema(description = "Volume semanal de partida do motor, em km, com até uma casa decimal", example = "40.0")
        @NotNull(message = "Informe o volume de partida")
        @Positive(message = "O volume de partida precisa ser positivo")
        @DecimalMax(value = "500", message = "O volume de partida vai até 500 km")
        @Digits(integer = 3, fraction = 1, message = "O volume de partida tem no máximo uma casa decimal")
        BigDecimal startVolumeKm
) {
}
