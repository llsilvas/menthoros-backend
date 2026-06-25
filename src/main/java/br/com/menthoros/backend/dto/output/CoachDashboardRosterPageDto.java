package br.com.menthoros.backend.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Página do roster do coach utilizada pelo dashboard agregado.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Página do roster do coach no dashboard")
public record CoachDashboardRosterPageDto(

        @Schema(description = "Itens da página")
        List<CoachAtletaResumoDto> items,

        @Schema(description = "Página zero-based", example = "0")
        int page,

        @Schema(description = "Tamanho da página", example = "10")
        int size,

        @Schema(description = "Número total de itens após filtro", example = "24")
        int totalElements,

        @Schema(description = "Número total de páginas", example = "3")
        int totalPages
) {}
