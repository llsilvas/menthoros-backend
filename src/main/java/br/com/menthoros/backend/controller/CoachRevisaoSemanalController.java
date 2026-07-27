package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.output.RevisaoSemanalOutputDto;
import br.com.menthoros.backend.security.RequireTenant;
import br.com.menthoros.backend.services.RevisaoSemanalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Revisão semanal de um atleta para o coach (Fatia 1 — add-weekly-review-consolidation).
 *
 * <p>Retorna a última revisão congelada (última semana fechada) com {@code weekOverWeekDelta}
 * computado. Restrito a {@code TECNICO}/{@code ADMIN}; tenant resolvido via {@code TenantContext}
 * e validado no service. Antes de qualquer semana encerrada → 404.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/coach/atletas")
@PreAuthorize("hasAnyRole('TECNICO', 'ADMIN')")
@Tag(name = "coach-revisao-semanal", description = "Revisão semanal do atleta para o treinador")
public class CoachRevisaoSemanalController {

    private final RevisaoSemanalService revisaoSemanalService;

    @GetMapping("/{atletaId}/revisao-semanal")
    @RequireTenant(resourceParamIndex = 0)
    @Operation(
            summary = "Última revisão semanal do atleta",
            description = "Retorna a revisão congelada da última semana fechada (recommendationType, "
                    + "aderência, sufficientData) com weekOverWeekDelta computado. Read-only.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Revisão carregada com sucesso",
                    content = @Content(schema = @Schema(implementation = RevisaoSemanalOutputDto.class))),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão ou atleta pertence a outro tenant"),
            @ApiResponse(responseCode = "404", description = "Sem revisão (nenhuma semana fechada)")
    })
    public ResponseEntity<RevisaoSemanalOutputDto> buscarUltima(
            @Parameter(description = "ID do atleta") @PathVariable UUID atletaId) {
        return ResponseEntity.ok(revisaoSemanalService.buscarUltima(atletaId));
    }
}
