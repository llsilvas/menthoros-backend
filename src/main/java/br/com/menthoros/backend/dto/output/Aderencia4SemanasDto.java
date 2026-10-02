package br.com.menthoros.backend.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Aderência da semana atual + 3 anteriores — mesma função que alimenta
 * {@code CoachAtletaResumoDto.aderenciaPercentual} (roster). Roster e perfil concordam por
 * construção: os dois chamam {@link br.com.menthoros.backend.services.AtletaProgressService#getAderencia4Semanas}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Aderência da semana atual + 3 anteriores (janela usada pelo roster do coach)")
public record Aderencia4SemanasDto(

        @Schema(description = "Treinos planejados devidos na janela que têm realizado vinculado contando na carga", example = "12")
        int realizado,

        @Schema(description = "Total de treinos planejados na janela", example = "16")
        int planejado,

        @Schema(description = "Percentual de aderência (0–100+, extras podem passar de 100)", example = "75")
        int percentual
) {}
