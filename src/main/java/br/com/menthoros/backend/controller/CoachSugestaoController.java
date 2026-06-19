package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.output.SugestaoCoachOutputDto;
import br.com.menthoros.backend.enums.StatusSugestao;
import br.com.menthoros.backend.security.RequireTenant;
import br.com.menthoros.backend.services.SugestaoCoachService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Inbox de sugestões de IA geradas pelo job diário para revisão do treinador.
 *
 * <p>Restrito a {@code TECNICO}/{@code ADMIN}. Tenant resolvido via {@code TenantContext}
 * no service para listagem; endpoints com {@code {id}} usam {@code @RequireTenant} para
 * validar que o recurso pertence ao tenant antes de qualquer operação.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/coach/sugestoes")
@PreAuthorize("hasAnyRole('TECNICO', 'ADMIN')")
@Tag(name = "coach-suggestion-inbox", description = "Sugestões de IA para revisão do treinador")
public class CoachSugestaoController {

    private final SugestaoCoachService sugestaoCoachService;

    @GetMapping
    @Operation(summary = "Lista sugestões por status",
            description = "Retorna sugestões do tenant filtradas por status. "
                    + "Para status=PENDING exclui sugestões expiradas.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de sugestões",
                    content = @Content(array = @ArraySchema(
                            schema = @Schema(implementation = SugestaoCoachOutputDto.class)))),
            @ApiResponse(responseCode = "400", description = "Status inválido"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (requer TECNICO/ADMIN)")
    })
    public ResponseEntity<List<SugestaoCoachOutputDto>> listar(
            @Parameter(description = "Filtro de status (PENDING, APPROVED, REJECTED); omitir retorna vazio")
            @RequestParam(required = false) StatusSugestao status) {
        return ResponseEntity.ok(sugestaoCoachService.listar(status));
    }

    @GetMapping("/{id}")
    @RequireTenant(resourceParamIndex = 0)
    @Operation(summary = "Detalhe de uma sugestão")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sugestão encontrada",
                    content = @Content(schema = @Schema(implementation = SugestaoCoachOutputDto.class))),
            @ApiResponse(responseCode = "403", description = "Sem permissão ou recurso de outro tenant"),
            @ApiResponse(responseCode = "404", description = "Sugestão não encontrada")
    })
    public ResponseEntity<SugestaoCoachOutputDto> detalhe(@PathVariable UUID id) {
        return ResponseEntity.ok(sugestaoCoachService.detalhe(id));
    }

    @PostMapping("/{id}/aprovar")
    @RequireTenant(resourceParamIndex = 0)
    @Operation(summary = "Aprova uma sugestão PENDING",
            description = "Transiciona PENDING → APPROVED. Re-aprovar já-APPROVED é no-op. "
                    + "REJECTED → APPROVED lança 422.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sugestão aprovada (ou já estava APPROVED — no-op)",
                    content = @Content(schema = @Schema(implementation = SugestaoCoachOutputDto.class))),
            @ApiResponse(responseCode = "403", description = "Sem permissão ou recurso de outro tenant"),
            @ApiResponse(responseCode = "404", description = "Sugestão não encontrada"),
            @ApiResponse(responseCode = "422", description = "Transição ilegal: REJECTED → APPROVED")
    })
    public ResponseEntity<SugestaoCoachOutputDto> aprovar(@PathVariable UUID id) {
        return ResponseEntity.ok(sugestaoCoachService.aprovar(id));
    }

    @PostMapping("/{id}/rejeitar")
    @RequireTenant(resourceParamIndex = 0)
    @Operation(summary = "Rejeita uma sugestão PENDING",
            description = "Transiciona PENDING → REJECTED. Re-rejeitar já-REJECTED é no-op. "
                    + "APPROVED → REJECTED lança 422.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sugestão rejeitada (ou já estava REJECTED — no-op)",
                    content = @Content(schema = @Schema(implementation = SugestaoCoachOutputDto.class))),
            @ApiResponse(responseCode = "403", description = "Sem permissão ou recurso de outro tenant"),
            @ApiResponse(responseCode = "404", description = "Sugestão não encontrada"),
            @ApiResponse(responseCode = "422", description = "Transição ilegal: APPROVED → REJECTED")
    })
    public ResponseEntity<SugestaoCoachOutputDto> rejeitar(@PathVariable UUID id) {
        return ResponseEntity.ok(sugestaoCoachService.rejeitar(id));
    }
}
