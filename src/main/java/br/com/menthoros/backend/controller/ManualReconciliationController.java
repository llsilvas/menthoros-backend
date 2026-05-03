package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.output.TreinoRealizadoOutputDto;
import br.com.menthoros.backend.dto.output.TreinoRealizadoPendenteOutputDto;
import br.com.menthoros.backend.dto.output.CandidateMatchDto;
import br.com.menthoros.backend.dto.input.ReconciliacaoAcaoRequestDto;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.mapper.TreinoMapper;
import br.com.menthoros.backend.service.ManualReconciliationService;
import br.com.menthoros.backend.service.ReconciliacaoPendentesService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import br.com.menthoros.backend.enums.ReconciliationStatus;

/**
 * Controller para ações manuais de reconciliação de atividades.
 * Endpoints para vincular, desmarcar e desfazer vínculo.
 */
@RestController
@RequestMapping("/api/v1/reconciliation")
public class ManualReconciliationController {

    private final ManualReconciliationService reconciliationService;
    private final ReconciliacaoPendentesService pendentesService;
    private final TreinoMapper treinoMapper;

    public ManualReconciliationController(
            ManualReconciliationService reconciliationService,
            ReconciliacaoPendentesService pendentesService,
            TreinoMapper treinoMapper) {
        this.reconciliationService = reconciliationService;
        this.pendentesService = pendentesService;
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

    /**
     * GET /api/v1/reconciliation/atletas/{atletaId}/pendentes
     * Lista atividades pendentes de reconciliação (AMBIGUO/NAO_PLANEJADO) por atleta com paginação.
     * Query param 'statuses' é opcional (default: AMBIGUO,NAO_PLANEJADO) e restrito a valores pendentes.
     */
    @GetMapping("/atletas/{atletaId}/pendentes")
    public ResponseEntity<Page<TreinoRealizadoPendenteOutputDto>> listarPendentes(
            @PathVariable UUID atletaId,
            @RequestParam(required = false) List<String> statuses,
            @RequestParam(required = false) LocalDate dataInicio,
            @RequestParam(required = false) LocalDate dataFim,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader("X-Tenant-ID") UUID tenantId) {

        List<ReconciliationStatus> statusList = null;
        if (statuses != null && !statuses.isEmpty()) {
            statusList = statuses.stream()
                    .map(s -> {
                        try {
                            return ReconciliationStatus.valueOf(s);
                        } catch (IllegalArgumentException e) {
                            throw new IllegalArgumentException("Status inválido: " + s);
                        }
                    })
                    .peek(status -> {
                        if (status != ReconciliationStatus.AMBIGUO && status != ReconciliationStatus.NAO_PLANEJADO) {
                            throw new IllegalArgumentException("Status não permitido para pendentes: " + status + ". Apenas AMBIGUO e NAO_PLANEJADO são permitidos.");
                        }
                    })
                    .collect(Collectors.toList());
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by("dataTreino").descending());
        Page<TreinoRealizadoPendenteOutputDto> dtos = pendentesService.listarPendentes(
                atletaId, tenantId, statusList, dataInicio, dataFim, pageable);
        return ResponseEntity.ok(dtos);
    }

    /**
     * GET /api/v1/reconciliation/{treinoRealizadoId}/candidatos
     * Lista candidatos de vínculo rankeados por score de compatibilidade.
     */
    @GetMapping("/{treinoRealizadoId}/candidatos")
    public ResponseEntity<List<CandidateMatchDto>> listarCandidatos(
            @PathVariable UUID treinoRealizadoId,
            @RequestHeader("X-Tenant-ID") UUID tenantId) {

        List<CandidateMatchDto> candidatos = pendentesService.listarCandidatos(
                treinoRealizadoId, tenantId);
        return ResponseEntity.ok(candidatos);
    }

    /**
     * POST /api/v1/reconciliation/{treinoRealizadoId}/acao
     * Executa uma ação de reconciliação unificada (vincular/marcar/desfazer).
     */
    @PostMapping("/{treinoRealizadoId}/acao")
    public ResponseEntity<TreinoRealizadoOutputDto> executarAcao(
            @PathVariable UUID treinoRealizadoId,
            @RequestBody @Valid ReconciliacaoAcaoRequestDto request,
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            Authentication authentication) {

        String actorId = authentication.getName();

        TreinoRealizado realizado = switch (request.action()) {
            case VINCULAR_MANUALMENTE -> {
                if (request.treinoPlanejadoId() == null) {
                    throw new IllegalArgumentException("treinoPlanejadoId é obrigatório para VINCULAR_MANUALMENTE");
                }
                yield reconciliationService.linkManually(
                        treinoRealizadoId,
                        request.treinoPlanejadoId(),
                        tenantId,
                        actorId
                );
            }
            case MARCAR_NAO_PLANEJADO -> reconciliationService.markAsNotPlanned(
                    treinoRealizadoId, tenantId, actorId);
            case DESFAZER_VINCULO -> reconciliationService.unlinkManually(
                    treinoRealizadoId, tenantId, actorId);
            default -> throw new IllegalArgumentException("Ação inválida: " + request.action());
        };

        TreinoRealizadoOutputDto dto = treinoMapper.toOutputDto(realizado);
        return ResponseEntity.ok(dto);
    }
}
