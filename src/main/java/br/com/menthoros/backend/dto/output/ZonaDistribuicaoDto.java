package br.com.menthoros.backend.dto.output;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Distribuição de tempo por zona de FC (z1–z5) no período, em segundos.
 *
 * <p>Campos {@code long} primitivos (sempre serializados, inclusive zero). {@code duracaoTotalSegundos}
 * é a soma de z1..z5 (tempo classificável por FC); etapas sem FC média não entram na distribuição.</p>
 */
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
