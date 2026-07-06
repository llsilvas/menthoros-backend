package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.input.BatchGeracaoPlanoInputDto;
import br.com.menthoros.backend.dto.output.BatchJobStatusOutputDto;
import br.com.menthoros.backend.dto.output.BatchLoteAceitoOutputDto;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.services.BatchPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

/**
 * Geração de planos de treino em lote pelo treinador.
 *
 * <p>Restrito a {@code TECNICO}/{@code ADMIN}. O disparo é assíncrono (202 + jobId) e o
 * acompanhamento é por polling. O tenant é resolvido do {@code TenantContext} na thread HTTP
 * e propagado ao fluxo assíncrono; o GET valida que o job pertence ao tenant corrente.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/coach")
@Tag(name = "coach-batch-plan", description = "Geração assíncrona de planos de treino em lote")
public class CoachBatchPlanController {

    private final BatchPlanService batchPlanService;

    @PostMapping("/planos/gerar-lote")
    @PreAuthorize("hasAnyRole('TECNICO', 'ADMIN')")
    @Operation(summary = "Dispara a geração de planos em lote",
            description = "Cria um job assíncrono para gerar planos de 1 a 20 atletas e retorna 202 imediatamente. "
                    + "Os planos entram em AGUARDANDO_REVISAO. IDs duplicados são deduplicados.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Lote aceito; use o jobId para polling",
                    content = @Content(schema = @Schema(implementation = BatchLoteAceitoOutputDto.class))),
            @ApiResponse(responseCode = "400", description = "Requisição inválida (lista vazia ou > 20 atletas)"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (requer TECNICO/ADMIN)")
    })
    public ResponseEntity<BatchLoteAceitoOutputDto> gerarLote(
            @Valid @RequestBody BatchGeracaoPlanoInputDto body) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        BatchLoteAceitoOutputDto aceito = batchPlanService.iniciarLote(body, tenantId);
        URI location = URI.create("/api/v1/coach/planos/lote/" + aceito.jobId());
        return ResponseEntity.accepted().location(location).body(aceito);
    }

    @GetMapping("/planos/lote/{jobId}")
    @PreAuthorize("hasAnyRole('TECNICO', 'ADMIN')")
    @Operation(summary = "Consulta o estado de um job de geração em lote",
            description = "Retorna o progresso (contadores) e, no estado terminal, os detalhes por atleta. "
                    + "Detalhes vêm vazios enquanto o job está em andamento.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado atual do job",
                    content = @Content(schema = @Schema(implementation = BatchJobStatusOutputDto.class))),
            @ApiResponse(responseCode = "403", description = "Sem permissão (requer TECNICO/ADMIN)"),
            @ApiResponse(responseCode = "404", description = "Job não encontrado no tenant")
    })
    public ResponseEntity<BatchJobStatusOutputDto> consultarLote(
            @Parameter(description = "ID do job de geração em lote") @PathVariable UUID jobId) {
        // @RequireTenant não é usado aqui de propósito: o aspecto valida recursos de domínio
        // conhecidos, não o BatchPlanJob. O isolamento é garantido no serviço via
        // findByIdAndTenantId(jobId, tenantId), que retorna 404 para job de outro tenant.
        UUID tenantId = TenantContext.getRequiredTenantId();
        return ResponseEntity.ok(batchPlanService.consultarStatus(jobId, tenantId));
    }
}
