package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.output.AnaliseWorkoutOutputDto;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.security.RequireTenant;
import br.com.menthoros.backend.services.AnaliseWorkoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/analises")
@Tag(name = "analise", description = "Consulta de análises pós-treino geradas por IA")
public class AnaliseWorkoutController {

    private final AnaliseWorkoutService analiseWorkoutService;

    @GetMapping("/treino/{treinoRealizadoId}")
    // Endpoint do COACH (analise-ia-treino-atleta, Codex #1): antes era isAuthenticated() e só o
    // tenant filtrava — com o bloco do atleta no DTO, um atleta leria a análise de outro. O
    // atleta tem o próprio endpoint (/atletas/me/realizados/{id}/analise), escopado por dono.
    @PreAuthorize("hasAnyRole('TECNICO','ADMIN')")
    @RequireTenant(resourceParamIndex = 0)
    @Operation(summary = "Busca a análise pós-treino de um treino realizado")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Análise encontrada (COMPLETED ou FAILED)"),
            @ApiResponse(responseCode = "204", description = "Análise ainda em processamento (PENDING) ou não iniciada"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Acesso negado — endpoint de coach (TECNICO/ADMIN)"),
            @ApiResponse(responseCode = "404", description = "Treino não encontrado ou não pertence ao tenant")
    })
    public ResponseEntity<AnaliseWorkoutOutputDto> getAnalise(
            @Parameter(description = "ID do treino realizado") @PathVariable UUID treinoRealizadoId) {

        return analiseWorkoutService
                .buscarAnalise(treinoRealizadoId, TenantContext.getRequiredTenantId())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }
}
