package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.output.TreinoRealizadoOutputDto;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.mapper.TreinoMapper;
import br.com.menthoros.backend.service.ManualReconciliationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controller para ações manuais de reconciliação de atividades.
 * Endpoints para vincular, desmarcar e desfazer vínculo.
 */
@RestController
@RequestMapping("/api/v1/reconciliation")
public class ManualReconciliationController {

    private final ManualReconciliationService reconciliationService;
    private final TreinoMapper treinoMapper;

    public ManualReconciliationController(
            ManualReconciliationService reconciliationService,
            TreinoMapper treinoMapper) {
        this.reconciliationService = reconciliationService;
        this.treinoMapper = treinoMapper;
    }

    /**
     * GET /api/v1/reconciliation/{id}
     * Recupera o estado de reconciliação de uma atividade.
     */
    @GetMapping("/{treinoRealizadoId}")
    public ResponseEntity<TreinoRealizadoOutputDto> getReconciliationState(
            @PathVariable UUID treinoRealizadoId,
            @RequestHeader("X-Tenant-ID") UUID tenantId) {

        TreinoRealizado realizado = reconciliationService.getReconciliationState(treinoRealizadoId, tenantId);
        TreinoRealizadoOutputDto dto = treinoMapper.toOutputDto(realizado);

        return ResponseEntity.ok(dto);
    }

    /**
     * POST /api/v1/reconciliation/{id}/link?treinoPlanejadoId=...
     * Vincula manualmente uma atividade realizada a um treino planejado.
     */
    @PostMapping("/{treinoRealizadoId}/link")
    public ResponseEntity<TreinoRealizadoOutputDto> linkManually(
            @PathVariable UUID treinoRealizadoId,
            @RequestParam UUID treinoPlanejadoId,
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            Authentication authentication) {

        String actorId = authentication.getName();

        TreinoRealizado realizado = reconciliationService.linkManually(
                treinoRealizadoId,
                treinoPlanejadoId,
                tenantId,
                actorId
        );

        TreinoRealizadoOutputDto dto = treinoMapper.toOutputDto(realizado);
        return ResponseEntity.ok(dto);
    }

    /**
     * POST /api/v1/reconciliation/{id}/mark-not-planned
     * Marca uma atividade realizada como não planejada (orfã).
     */
    @PostMapping("/{treinoRealizadoId}/mark-not-planned")
    public ResponseEntity<TreinoRealizadoOutputDto> markAsNotPlanned(
            @PathVariable UUID treinoRealizadoId,
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            Authentication authentication) {

        String actorId = authentication.getName();

        TreinoRealizado realizado = reconciliationService.markAsNotPlanned(
                treinoRealizadoId,
                tenantId,
                actorId
        );

        TreinoRealizadoOutputDto dto = treinoMapper.toOutputDto(realizado);
        return ResponseEntity.ok(dto);
    }

    /**
     * POST /api/v1/reconciliation/{id}/unlink
     * Desfaz o vínculo de uma atividade realizada com um treino planejado.
     */
    @PostMapping("/{treinoRealizadoId}/unlink")
    public ResponseEntity<TreinoRealizadoOutputDto> unlinkManually(
            @PathVariable UUID treinoRealizadoId,
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            Authentication authentication) {

        String actorId = authentication.getName();

        TreinoRealizado realizado = reconciliationService.unlinkManually(
                treinoRealizadoId,
                tenantId,
                actorId
        );

        TreinoRealizadoOutputDto dto = treinoMapper.toOutputDto(realizado);
        return ResponseEntity.ok(dto);
    }
}
