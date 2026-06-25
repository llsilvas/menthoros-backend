package br.com.menthoros.backend.dto.input;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/**
 * Filtros e paginação do dashboard agregado do coach.
 */
@Schema(description = "Filtros e paginação do dashboard do coach")
public record CoachDashboardQueryDto(

        @Schema(description = "Busca textual por nome do atleta", example = "Bruno")
        String q,

        @Schema(description = "Status do roster: active, warning, danger ou paused", example = "warning")
        String status,

        @Schema(description = "Ordenação da lista: priority, name, volume ou tsb", example = "priority")
        String sortBy,

        @Schema(description = "Página zero-based da lista", example = "0")
        Integer page,

        @Schema(description = "Tamanho da página", example = "10")
        Integer size,

        @Schema(description = "Início do intervalo de insights", example = "2026-06-01")
        LocalDate from,

        @Schema(description = "Fim do intervalo de insights", example = "2026-07-15")
        LocalDate to,

        @Schema(description = "Dia dentro da semana desejada para o calendário", example = "2026-06-24")
        LocalDate weekFrom
) {}
