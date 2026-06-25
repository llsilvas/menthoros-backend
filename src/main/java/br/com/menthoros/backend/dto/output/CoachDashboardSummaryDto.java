package br.com.menthoros.backend.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Resumo operacional do dashboard do coach.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Resumo operacional do dashboard do coach")
public record CoachDashboardSummaryDto(

        @Schema(description = "KPIs consolidados do tenant")
        CoachInsightsDto.Kpis kpis,

        @Schema(description = "Quantidade de atletas exibidos após os filtros aplicados", example = "8")
        int atletasExibidos,

        @Schema(description = "Quantidade de itens na fila de atenção", example = "3")
        int itensFilaAtencao
) {}
