package br.com.menthoros.backend.dto.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * Resposta agregada do dashboard do coach.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Resposta agregada do dashboard do coach")
public record CoachDashboardOutputDto(

        @Schema(description = "Momento em que o dashboard foi montado")
        Instant generatedAt,

        @Schema(description = "Resumo operacional da tela")
        CoachDashboardSummaryDto summary,

        @Schema(description = "Roster paginado e filtrado")
        CoachDashboardRosterPageDto roster,

        @Schema(description = "Fila de atenção priorizada")
        List<CoachAttentionItemOutputDto> attentionQueue,

        @Schema(description = "Calendário semanal agregado")
        CoachCalendarioDto calendar,

        @Schema(description = "Insights agregados do tenant")
        CoachInsightsDto insights
) {}
