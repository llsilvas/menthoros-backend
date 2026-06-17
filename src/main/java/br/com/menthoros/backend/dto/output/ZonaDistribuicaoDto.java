package br.com.menthoros.backend.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Distribuição de tempo por zona de FC (z1–z5) no período, em segundos.
 *
 * <p>{@code duracaoTotalSegundos} é a soma de z1..z5 (tempo classificável por FC).
 * Etapas sem FC média registrada não entram na distribuição nem no total.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Distribuição de tempo por zona de FC (segundos) no período")
public record ZonaDistribuicaoDto(

        @Schema(description = "Segundos na zona 1 (recuperação)", example = "3600")
        long z1,

        @Schema(description = "Segundos na zona 2 (aeróbico)", example = "7200")
        long z2,

        @Schema(description = "Segundos na zona 3 (tempo)", example = "1800")
        long z3,

        @Schema(description = "Segundos na zona 4 (limiar)", example = "900")
        long z4,

        @Schema(description = "Segundos na zona 5 (VO2max)", example = "300")
        long z5,

        @Schema(description = "Soma de z1..z5 (tempo classificável por FC)", example = "13800")
        long duracaoTotalSegundos
) {}
