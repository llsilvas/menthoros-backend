package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.input.CoachDashboardQueryDto;
import br.com.menthoros.backend.dto.output.CoachDashboardOutputDto;
import br.com.menthoros.backend.dto.output.CoachAtletaResumoDto;
import br.com.menthoros.backend.dto.output.CoachCalendarioDto;
import br.com.menthoros.backend.dto.output.CoachInsightsDto;
import br.com.menthoros.backend.services.CoachDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Dashboards do coach (roster, calendário, insights), agregados por tenant.
 *
 * <p>Restrito a {@code TECNICO}/{@code ADMIN}. Endpoints agregam por tenant (resolvido via
 * {@code TenantContext} no serviço) e não recebem resource-id — por isso não usam
 * {@code @RequireTenant} (que valida um parâmetro de recurso). Mesmo padrão do
 * {@code AssessoriaMetricasController}.</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/coach")
@Tag(name = "coach-dashboard", description = "Roster, calendário semanal e insights agregados por tenant")
public class CoachDashboardController {

    private final CoachDashboardService coachDashboardService;

    @GetMapping("/atletas")
    @PreAuthorize("hasAnyRole('TECNICO', 'ADMIN')")
    @Operation(summary = "Roster do coach",
            description = "Atletas do tenant com CTL/ATL/TSB, fase, status, última atividade e volume da semana.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Roster retornado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (requer TECNICO/ADMIN)"),
            @ApiResponse(responseCode = "401", description = "Não autenticado")
    })
    public ResponseEntity<List<CoachAtletaResumoDto>> getRoster() {
        return ResponseEntity.ok(coachDashboardService.getRoster());
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('TECNICO', 'ADMIN')")
    @Operation(summary = "Dashboard agregado do coach",
            description = "Agrega em uma chamada o resumo do tenant, fila de atenção, roster filtrado, "
                    + "calendário semanal e insights do período. Read-only e tenant-aware.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dashboard retornado"),
            @ApiResponse(responseCode = "400", description = "Parâmetros de consulta inválidos"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (requer TECNICO/ADMIN)"),
            @ApiResponse(responseCode = "401", description = "Não autenticado")
    })
    public ResponseEntity<CoachDashboardOutputDto> getDashboard(
            @Parameter(description = "Busca textual por nome do atleta")
            @RequestParam(required = false) String q,
            @Parameter(description = "Status do roster: active, warning, danger ou paused")
            @RequestParam(required = false) String status,
            @Parameter(description = "Ordenação da lista: priority, name, volume ou tsb")
            @RequestParam(required = false) String sortBy,
            @Parameter(description = "Página zero-based da lista")
            @RequestParam(required = false) Integer page,
            @Parameter(description = "Tamanho da página")
            @RequestParam(required = false) Integer size,
            @Parameter(description = "Início do intervalo de insights")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Fim do intervalo de insights")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Dia dentro da semana desejada para o calendário")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekFrom) {
        CoachDashboardQueryDto query = new CoachDashboardQueryDto(q, status, sortBy, page, size, from, to, weekFrom);
        return ResponseEntity.ok(coachDashboardService.getDashboard(query));
    }

    @GetMapping("/calendario-semanal")
    @PreAuthorize("hasAnyRole('TECNICO', 'ADMIN')")
    @Operation(summary = "Calendário semanal do coach",
            description = "Treinos planejados de todos os atletas do tenant na semana de 'from' (default: semana atual).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Calendário retornado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (requer TECNICO/ADMIN)"),
            @ApiResponse(responseCode = "401", description = "Não autenticado")
    })
    public ResponseEntity<CoachCalendarioDto> getCalendarioSemanal(
            @Parameter(description = "Dia dentro da semana desejada (ISO-8601); default semana atual")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from) {
        return ResponseEntity.ok(coachDashboardService.getCalendarioSemanal(from));
    }

    @GetMapping("/insights")
    @PreAuthorize("hasAnyRole('TECNICO', 'ADMIN')")
    @Operation(summary = "Insights agregados do coach",
            description = "KPIs do tenant, tendência de carga semanal e top atletas no intervalo (default: últimas 12 semanas).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Insights retornados"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (requer TECNICO/ADMIN)"),
            @ApiResponse(responseCode = "401", description = "Não autenticado")
    })
    public ResponseEntity<CoachInsightsDto> getInsights(
            @Parameter(description = "Início do intervalo (ISO-8601); default 12 semanas atrás")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Fim do intervalo (ISO-8601); default hoje")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(coachDashboardService.getInsights(from, to));
    }
}
