package br.com.menthoros.backend.dto.output;

import br.com.menthoros.backend.enums.RecommendationType;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Comparação da revisão com a semana (revisão) anterior do atleta — computada na leitura,
 * não persistida (CA9). Sem revisão anterior ⇒ {@link #semAnterior()} ({@code primeiraSemana=true}).
 */
@Schema(description = "Delta da revisão vs. a semana anterior; deltas nulos quando é a primeira semana")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WeekOverWeekDelta(
        @Schema(description = "true quando não há revisão anterior (deltas nulos)")
        boolean primeiraSemana,

        @Schema(description = "Δ do % de aderência vs. a semana anterior", example = "30.00")
        BigDecimal deltaPercentualRealizacao,

        @Schema(description = "Δ do TSB final vs. a semana anterior", example = "10.00")
        BigDecimal deltaTsbFim,

        @Schema(description = "recommendationType da semana anterior", example = "MAINTAIN")
        RecommendationType recommendationAnterior
) {

    public static WeekOverWeekDelta semAnterior() {
        return new WeekOverWeekDelta(true, null, null, null);
    }
}
