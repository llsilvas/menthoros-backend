package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.output.TreinoRealizadoOutputDto;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.security.RequireTenant;
import br.com.menthoros.backend.services.IntervalsIcuActivityIngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Importa (pull) uma atividade específica do intervals.icu como {@code TreinoRealizado} (design.md
 * D5). Não confundir com o controller de conexão {@code me/} (self-service do atleta) — este é
 * coach-facing.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/intervals-icu")
@Tag(name = "intervals-icu-activities", description = "Import de atividades realizadas do intervals.icu")
public class IntervalsIcuActivityController {

    private final IntervalsIcuActivityIngestionService intervalsIcuActivityIngestionService;

    @PostMapping("/atletas/{atletaId}/activities/import")
    @PreAuthorize("hasAnyRole('TECNICO', 'ADMIN')")
    @RequireTenant(resourceParamIndex = 0)
    @Operation(summary = "Importa uma atividade específica do intervals.icu como treino realizado")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Atividade importada (ou já existente, retornada por idempotência)"),
        @ApiResponse(responseCode = "401", description = "Não autenticado"),
        @ApiResponse(responseCode = "403", description = "Sem papel TECNICO/ADMIN ou atleta de outro tenant"),
        @ApiResponse(responseCode = "404", description = "Atleta, conexão intervals.icu ou activity não encontrados"),
        @ApiResponse(responseCode = "409", description = "Precondição de conexão/Strava não satisfeita (ver D5.2)"),
        @ApiResponse(responseCode = "422", description = "Modalidade não suportada ou activity rejeitada pelo intervals.icu"),
        @ApiResponse(responseCode = "429", description = "Limite de requisições do intervals.icu atingido")
    })
    public ResponseEntity<TreinoRealizadoOutputDto> importarAtividade(
            @Parameter(description = "ID único do atleta") @PathVariable UUID atletaId,
            @Parameter(description = "ID da activity no intervals.icu — aceita a URL completa colada")
            @RequestParam String activityId) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        return ResponseEntity.ok(intervalsIcuActivityIngestionService.importarAtividade(atletaId, activityId, tenantId));
    }
}
