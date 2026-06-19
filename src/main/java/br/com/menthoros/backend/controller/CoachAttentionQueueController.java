package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.output.CoachAttentionItemOutputDto;
import br.com.menthoros.backend.services.CoachAttentionQueueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Fila de atenção do treinador: atletas priorizados que exigem ação, agregados por tenant.
 *
 * <p>Restrito a {@code TECNICO}/{@code ADMIN}. Read-only e on-demand; agrega por tenant (resolvido via
 * {@code TenantContext} no serviço) e não recebe resource-id — por isso não usa {@code @RequireTenant}.
 * Mesmo padrão do {@code CoachDashboardController}.</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/coach")
@Tag(name = "coach-attention-queue", description = "Fila priorizada de atletas que exigem ação do treinador")
public class CoachAttentionQueueController {

    private final CoachAttentionQueueService coachAttentionQueueService;

    @GetMapping("/attention-queue")
    @PreAuthorize("hasAnyRole('TECNICO', 'ADMIN')")
    @Operation(summary = "Fila de atenção do treinador",
            description = "Atletas do tenant priorizados por severidade (apenas ALTA/CRITICA na v1), "
                    + "com motivo principal, ação sugerida e evidências. Read-only e on-demand.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Fila de atenção retornada"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (requer TECNICO/ADMIN)"),
            @ApiResponse(responseCode = "401", description = "Não autenticado")
    })
    public ResponseEntity<List<CoachAttentionItemOutputDto>> getAttentionQueue() {
        return ResponseEntity.ok(coachAttentionQueueService.getAttentionQueue());
    }
}
