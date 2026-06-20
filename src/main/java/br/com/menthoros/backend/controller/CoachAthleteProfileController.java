package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.output.AtletaPerfilCoachOutputDto;
import br.com.menthoros.backend.security.RequireTenant;
import br.com.menthoros.backend.services.CoachAthleteProfileService;
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
 * Perfil detalhado de um atleta para o coach — endpoint único agregador.
 *
 * <p>Retorna em uma chamada: PMC 90d, aderência 8 semanas, plano vigente, sinais recentes,
 * sugestões recentes e recordes pessoais. Restrito a {@code TECNICO}/{@code ADMIN}.
 * Tenant resolvido via {@code TenantContext} e validado no service.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/coach/atletas")
@PreAuthorize("hasAnyRole('TECNICO', 'ADMIN')")
@Tag(name = "coach-athlete-profile", description = "Perfil detalhado de atleta para o treinador")
public class CoachAthleteProfileController {

    private final CoachAthleteProfileService coachAthleteProfileService;

    @GetMapping("/{atletaId}/perfil")
    @RequireTenant(resourceParamIndex = 0)
    @Operation(
            summary = "Perfil completo de um atleta",
            description = "Agrega em uma chamada: PMC 90d, aderência 8 semanas, plano vigente, "
                    + "sinais recentes, sugestões recentes e recordes pessoais. "
                    + "Falhas parciais são registradas em 'avisos' e os demais blocos continuam carregados.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil carregado com sucesso",
                    content = @Content(schema = @Schema(implementation = AtletaPerfilCoachOutputDto.class))),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão ou atleta pertence a outro tenant"),
            @ApiResponse(responseCode = "404", description = "Atleta não encontrado no tenant")
    })
    public ResponseEntity<AtletaPerfilCoachOutputDto> buscarPerfil(
            @Parameter(description = "ID do atleta") @PathVariable UUID atletaId) {
        return ResponseEntity.ok(coachAthleteProfileService.buscarPerfil(atletaId));
    }
}
