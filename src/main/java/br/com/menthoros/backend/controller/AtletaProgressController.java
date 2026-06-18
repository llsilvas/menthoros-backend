package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.output.AtletaHomeDto;
import br.com.menthoros.backend.dto.output.PmcPontoDto;
import br.com.menthoros.backend.dto.output.ReadinessDto;
import br.com.menthoros.backend.dto.output.RecordeDto;
import br.com.menthoros.backend.dto.output.ZonaDistribuicaoDto;
import br.com.menthoros.backend.services.AtletaProgressService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Endpoints read-only de progresso do atleta para o shell.
 *
 * <p>Isolamento de tenant nos endpoints {@code {id}} é garantido na camada de serviço
 * (consulta tenant-scoped → 404 cross-tenant, conforme spec) — por isso NÃO usam
 * {@code @RequireTenant} (cujo {@code AccessDeniedException} mapearia para 403). Endpoints
 * {@code me/*} são auto-resolvidos pelo JWT, sem parâmetro de recurso.</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/atletas")
@Tag(name = "progresso-atleta", description = "PMC, zonas, recordes, readiness e resumo do dia")
public class AtletaProgressController {

    private final AtletaProgressService atletaProgressService;

    @GetMapping("/{id}/metricas/historico")
    @PreAuthorize("hasAnyRole('TECNICO', 'ADMIN')")
    @Operation(summary = "Histórico PMC do atleta",
            description = "Série diária CTL/ATL/TSB/TSS no intervalo (default: últimos 90 dias).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Série PMC retornada"),
            @ApiResponse(responseCode = "404", description = "Atleta não encontrado no tenant"),
            @ApiResponse(responseCode = "422", description = "Intervalo inválido (from > to)"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (requer TECNICO/ADMIN)"),
            @ApiResponse(responseCode = "401", description = "Não autenticado")
    })
    public ResponseEntity<List<PmcPontoDto>> getHistoricoPmc(
            @Parameter(description = "ID do atleta", required = true) @PathVariable UUID id,
            @Parameter(description = "Início do intervalo (ISO-8601); default 90 dias atrás")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Fim do intervalo (ISO-8601); default hoje")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(atletaProgressService.getHistoricoPmc(id, from, to));
    }

    @GetMapping("/{id}/metricas/zonas")
    @PreAuthorize("hasAnyRole('TECNICO', 'ADMIN')")
    @Operation(summary = "Distribuição de zonas do atleta",
            description = "Tempo por zona de FC (z1–z5) no intervalo (default: últimos 90 dias).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Distribuição de zonas retornada"),
            @ApiResponse(responseCode = "404", description = "Atleta não encontrado no tenant"),
            @ApiResponse(responseCode = "422", description = "Intervalo inválido (from > to)"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (requer TECNICO/ADMIN)"),
            @ApiResponse(responseCode = "401", description = "Não autenticado")
    })
    public ResponseEntity<ZonaDistribuicaoDto> getDistribuicaoZonas(
            @Parameter(description = "ID do atleta", required = true) @PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(atletaProgressService.getDistribuicaoZonas(id, from, to));
    }

    @GetMapping("/{id}/recordes")
    @PreAuthorize("hasAnyRole('TECNICO', 'ADMIN')")
    @Operation(summary = "Recordes pessoais do atleta",
            description = "PRs (5k/10k/21k) derivados dos treinos realizados.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Recordes retornados"),
            @ApiResponse(responseCode = "404", description = "Atleta não encontrado no tenant"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (requer TECNICO/ADMIN)"),
            @ApiResponse(responseCode = "401", description = "Não autenticado")
    })
    public ResponseEntity<List<RecordeDto>> getRecordes(
            @Parameter(description = "ID do atleta", required = true) @PathVariable UUID id) {
        return ResponseEntity.ok(atletaProgressService.getRecordes(id));
    }

    @GetMapping("/me/readiness")
    @PreAuthorize("hasRole('ATLETA')")
    @Operation(summary = "Readiness atual do atleta autenticado",
            description = "Score + fatores objetivos (provisório, sem check-in subjetivo). Resolve o atleta do token.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Readiness retornado (defaults quando sem sinais)"),
            @ApiResponse(responseCode = "404", description = "Atleta do usuário autenticado não encontrado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (requer ATLETA)"),
            @ApiResponse(responseCode = "401", description = "Não autenticado")
    })
    public ResponseEntity<ReadinessDto> getReadinessAtual() {
        UUID atletaId = atletaProgressService.resolverAtletaIdAtual();
        return ResponseEntity.ok(atletaProgressService.getReadinessAtual(atletaId));
    }

    @GetMapping("/me/home")
    @PreAuthorize("hasRole('ATLETA')")
    @Operation(summary = "Resumo do dia do atleta autenticado",
            description = "Próximo treino planejado + métricas-chave. Resolve o atleta do token.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resumo retornado"),
            @ApiResponse(responseCode = "404", description = "Atleta do usuário autenticado não encontrado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (requer ATLETA)"),
            @ApiResponse(responseCode = "401", description = "Não autenticado")
    })
    public ResponseEntity<AtletaHomeDto> getHome() {
        UUID atletaId = atletaProgressService.resolverAtletaIdAtual();
        return ResponseEntity.ok(atletaProgressService.getHome(atletaId));
    }
}
