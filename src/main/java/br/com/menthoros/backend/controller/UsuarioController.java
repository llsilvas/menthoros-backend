package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.output.UsuarioMeOutputDto;
import br.com.menthoros.backend.services.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Sem @RequireTenant: a anotação é por método e valida um param de ID de recurso
// (resourceParamIndex). /me é self-resolving (resolve o caller pelo sub do JWT, sem param de
// recurso). O isolamento de tenant vem do JwtTenantFilter (popula TenantContext) +
// getRequiredTenantId() no service + query tenant-scoped (findByKeycloakIdAndAssessoria_Id).
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Usuários", description = "Identidade do usuário autenticado")
public class UsuarioController {

    private final UsuarioService usuarioService;

    @Operation(summary = "Retorna a identidade do usuário autenticado (role, dados básicos, "
            + "assessoria e atletaId quando ATLETA vinculado)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Identidade retornada com sucesso",
                    content = @Content(schema = @Schema(implementation = UsuarioMeOutputDto.class))),
            @ApiResponse(responseCode = "401", description = "Requisição sem JWT válido",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Usuário não encontrado no tenant atual",
                    content = @Content)
    })
    @GetMapping("/me")
    public ResponseEntity<UsuarioMeOutputDto> getMe() {
        return ResponseEntity.ok(usuarioService.getCurrentUser());
    }
}
