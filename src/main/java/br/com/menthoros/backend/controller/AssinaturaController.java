package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.input.AssinaturaInputDto;
import br.com.menthoros.backend.dto.input.AssinaturaTierInputDto;
import br.com.menthoros.backend.dto.output.AssinaturaOutputDto;
import br.com.menthoros.backend.services.AssinaturaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Endpoints administrativos de cobrança das assessorias (tenants).
 *
 * <p><strong>Autorização:</strong> {@code hasRole('ADMIN')} aqui é o role de <em>staff da
 * plataforma Menthoros</em> (não o admin por-tenant de uma assessoria) — mesma premissa e padrão do
 * {@code AssessoriaController} (que cria/gerencia tenants sob {@code /api/admin/**}). Por isso não há
 * tenant-scoping por {@code TenantContext}: são operações cross-tenant de back-office. Se o role
 * {@code ADMIN} vier a ser emitido também para admins de assessoria, estes endpoints (e o
 * {@code AssessoriaController}) precisarão de um role de plataforma distinto.
 */
@RestController
@RequestMapping("/api/admin/assessorias/{id}/assinatura")
@RequiredArgsConstructor
@Tag(name = "assinaturas", description = "Operações administrativas de cobrança (assinatura) das assessorias via Asaas")
public class AssinaturaController {

    private final AssinaturaService assinaturaService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Criar assinatura", description = "Cria a assinatura de cobrança da assessoria no Asaas (cartão tokenizado, nextDueDate configurável)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Assinatura criada",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AssinaturaOutputDto.class))),
            @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "403", description = "Acesso negado - apenas ADMIN", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "404", description = "Assessoria não encontrada", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "409", description = "Assessoria já possui assinatura ativa", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "502", description = "Falha na integração com o Asaas", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<AssinaturaOutputDto> criar(
            @PathVariable("id") UUID assessoriaId,
            @Valid @RequestBody AssinaturaInputDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(assinaturaService.criar(assessoriaId, dto));
    }

    @PatchMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Trocar tier", description = "Troca o tier da assessoria e atualiza o valor da assinatura no Asaas na mesma operação")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Tier atualizado",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AssinaturaOutputDto.class))),
            @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "403", description = "Acesso negado - apenas ADMIN", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "404", description = "Assessoria ou assinatura não encontrada", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "409", description = "Assinatura sem subscription ativa no Asaas", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "502", description = "Falha na integração com o Asaas", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<AssinaturaOutputDto> atualizarTier(
            @PathVariable("id") UUID assessoriaId,
            @Valid @RequestBody AssinaturaTierInputDto dto) {
        return ResponseEntity.ok(assinaturaService.atualizarTier(assessoriaId, dto));
    }

    @DeleteMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cancelar assinatura", description = "Cancela a assinatura no Asaas e desativa a assessoria")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Assinatura cancelada"),
            @ApiResponse(responseCode = "403", description = "Acesso negado - apenas ADMIN", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "404", description = "Assessoria ou assinatura não encontrada", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "502", description = "Falha na integração com o Asaas", content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<Void> cancelar(@PathVariable("id") UUID assessoriaId) {
        assinaturaService.cancelar(assessoriaId);
        return ResponseEntity.noContent().build();
    }
}
