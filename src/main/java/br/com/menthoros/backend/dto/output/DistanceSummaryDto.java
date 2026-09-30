package br.com.menthoros.backend.dto.output;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Distância realizada do atleta: por semana ISO e nas duas janelas de 7 dias usadas para comparar
 * carga. Treinos cancelados ficam de fora ({@code TreinoRealizado#contaNaCarga}).
 */
@Schema(description = "Distância realizada por semana e nos últimos 7 dias vs. os 7 anteriores")
public record DistanceSummaryDto(

        @ArraySchema(schema = @Schema(implementation = WeeklyDistanceDto.class),
                arraySchema = @Schema(description = "Semanas ISO contínuas, da mais antiga para a atual; 0 onde não houve treino"))
        List<WeeklyDistanceDto> weekly,

        @Schema(description = "Km realizados nos últimos 7 dias, hoje inclusive", example = "5.0")
        BigDecimal last7DaysKm,

        @Schema(description = "Km realizados nos 7 dias anteriores a esses", example = "3.7")
        BigDecimal previous7DaysKm
) {

    @Schema(description = "Km realizados numa semana ISO (segunda a domingo)")
    public record WeeklyDistanceDto(

            @Schema(description = "Segunda-feira que abre a semana", example = "2026-09-21")
            LocalDate weekStart,

            @Schema(description = "Km realizados na semana", example = "34.2")
            BigDecimal distanceKm
    ) {}
}
