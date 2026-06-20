package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.input.PlanoRejectionInputDto;
import br.com.menthoros.backend.dto.output.PlanoSemanalOutputDto;
import br.com.menthoros.backend.enums.PlanoReviewStatus;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.security.RequireTenant;
import br.com.menthoros.backend.services.PlanoReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Revisão editorial de planos semanais gerados pela IA.
 *
 * <p>Restrito a {@code TECNICO}/{@code ADMIN}. Tenant resolvido via {@code TenantContext}.
 * Endpoints de mutação ({@code aprovar}/{@code rejeitar}) usam {@code @RequireTenant}
 * para validar que o plano pertence ao tenant antes de qualquer operação.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/coach/planos")
@Tag(name = "coach-plan-review", description = "Revisão e aprovação de planos semanais gerados pela IA")
public class CoachPlanoReviewController {

    private final PlanoReviewService planoReviewService;

    @GetMapping("/pendentes")
    @PreAuthorize("hasAnyRole('TECNICO', 'ADMIN')")
    @Operation(summary = "Lista planos pendentes de revisão",
            description = "Retorna todos os planos do tenant com reviewStatus = AGUARDANDO_REVISAO, "
                    + "ordenados por semana de início (mais antigos primeiro).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de planos pendentes",
                    content = @Content(array = @ArraySchema(
                            schema = @Schema(implementation = PlanoSemanalOutputDto.class)))),
            @ApiResponse(responseCode = "403", description = "Sem permissão (requer TECNICO/ADMIN)")
    })
    public ResponseEntity<List<PlanoSemanalOutputDto>> listarPendentes() {
        UUID tenantId = TenantContext.getRequiredTenantId();
        return ResponseEntity.ok(planoReviewService.listarPlanosPendentes(tenantId));
    }

    @GetMapping("/revisao")
    @PreAuthorize("hasAnyRole('TECNICO', 'ADMIN')")
    @Operation(summary = "Lista planos por status de revisão",
            description = "Retorna todos os planos do tenant com o reviewStatus informado, "
                    + "ordenados por semana de início (mais antigos primeiro).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de planos",
                    content = @Content(array = @ArraySchema(
                            schema = @Schema(implementation = PlanoSemanalOutputDto.class)))),
            @ApiResponse(responseCode = "400", description = "Status de revisão inválido"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (requer TECNICO/ADMIN)")
    })
    public ResponseEntity<List<PlanoSemanalOutputDto>> listarPorStatus(
            @Parameter(description = "Status de revisão: AGUARDANDO_REVISAO, APROVADO ou REJEITADO")
            @RequestParam PlanoReviewStatus status) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        return ResponseEntity.ok(planoReviewService.listarPlanosPorStatus(tenantId, status));
    }

    @PostMapping("/{id}/aprovar")
    @PreAuthorize("hasAnyRole('TECNICO', 'ADMIN')")
    @RequireTenant(resourceParamIndex = 0)
    @Operation(summary = "Aprova um plano AGUARDANDO_REVISAO",
            description = "Transiciona AGUARDANDO_REVISAO → APROVADO. "
                    + "Transições de APROVADO ou REJEITADO lançam 422.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Plano aprovado",
                    content = @Content(schema = @Schema(implementation = PlanoSemanalOutputDto.class))),
            @ApiResponse(responseCode = "403", description = "Sem permissão ou recurso de outro tenant"),
            @ApiResponse(responseCode = "404", description = "Plano não encontrado no tenant"),
            @ApiResponse(responseCode = "422", description = "Transição ilegal (plano não está AGUARDANDO_REVISAO)")
    })
    public ResponseEntity<PlanoSemanalOutputDto> aprovar(
            @Parameter(description = "ID do PlanoSemanal a aprovar")
            @PathVariable UUID id) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        return ResponseEntity.ok(planoReviewService.aprovarPlano(id, tenantId));
    }

    @PostMapping("/{id}/rejeitar")
    @PreAuthorize("hasAnyRole('TECNICO', 'ADMIN')")
    @RequireTenant(resourceParamIndex = 0)
    @Operation(summary = "Rejeita um plano AGUARDANDO_REVISAO",
            description = "Transiciona AGUARDANDO_REVISAO → REJEITADO com motivo obrigatório. "
                    + "Transições de APROVADO ou REJEITADO lançam 422.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Plano rejeitado",
                    content = @Content(schema = @Schema(implementation = PlanoSemanalOutputDto.class))),
            @ApiResponse(responseCode = "400", description = "Motivo ausente ou em branco"),
            @ApiResponse(responseCode = "403", description = "Sem permissão ou recurso de outro tenant"),
            @ApiResponse(responseCode = "404", description = "Plano não encontrado no tenant"),
            @ApiResponse(responseCode = "422", description = "Transição ilegal (plano não está AGUARDANDO_REVISAO)")
    })
    public ResponseEntity<PlanoSemanalOutputDto> rejeitar(
            @Parameter(description = "ID do PlanoSemanal a rejeitar")
            @PathVariable UUID id,
            @RequestBody @Valid PlanoRejectionInputDto input) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        return ResponseEntity.ok(planoReviewService.rejeitarPlano(id, tenantId, input.motivo()));
    }
}
