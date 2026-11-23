package br.com.menthoros.backend.dto.input;

import br.com.menthoros.backend.domain.planner.TrainingPhase;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Edição de uma semana do ajuste da temporada (add-season-plan-adjust-v2). Campo nulo não muda; em
 * {@code keySessions}, string vazia limpa.
 */
@Schema(description = "Novos valores de uma semana da temporada em ajuste; campo ausente fica como está")
public record SeasonWeekUpdateInputDto(
        @Schema(description = "Fase: BASE, BUILD, PEAK ou TAPER (RACE_WEEK é só da semana da prova)", example = "BUILD")
        TrainingPhase phase,

        @Schema(description = "TSS-alvo", example = "320")
        @PositiveOrZero(message = "O TSS-alvo não pode ser negativo")
        Double targetTss,

        @Schema(description = "Volume-alvo em km", example = "45.0")
        @DecimalMin(value = "0", message = "O volume-alvo não pode ser negativo")
        BigDecimal targetVolumeKm,

        @Schema(description = "Longão-alvo em km", example = "16.0")
        @DecimalMin(value = "0", message = "O longão não pode ser negativo")
        BigDecimal longRunKm,

        @Schema(description = "Semana de descarga")
        Boolean deload,

        @Schema(description = "Sessões-chave da semana; vazio limpa", example = "Intervalado 6x1km; longão progressivo")
        @Size(max = 500, message = "As sessões-chave têm no máximo 500 caracteres")
        String keySessions
) {
}
