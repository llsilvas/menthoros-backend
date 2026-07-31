package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.input.ConsentInputDto;
import br.com.menthoros.backend.dto.output.UsuarioMeOutputDto;
import br.com.menthoros.backend.services.UsuarioService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Sem @RequireTenant: a anotação é por método e valida um param de ID de recurso
// (resourceParamIndex). /me é self-resolving (resolve o caller pelo sub do JWT, sem param de
// recurso). O isolamento de tenant vem do JwtTenantFilter (popula TenantContext) +
// getRequiredTenantId() no service + query tenant-scoped (findByKeycloakIdAndAssessoria_Id).
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "usuarios", description = "Identidade do usuário autenticado")
public class UsuarioController {

    private final UsuarioService usuarioService;

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
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
    public ResponseEntity<UsuarioMeOutputDto> getMe() {
        return ResponseEntity.ok(usuarioService.getCurrentUser());
    }

    // Sem @RequireTenant pelo mesmo motivo do /me acima: self-resolving pelo sub do JWT, sem param
    // de recurso. O isolamento vem do JwtTenantFilter + query tenant-scoped no service, que ainda
    // valida explicitamente que o tenant do usuário bate com o TenantContext antes de gravar.
    @PostMapping("/me/consent")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Registra o aceite dos Termos de Uso e da Política de Privacidade",
            description = "Idempotente: reenviar o mesmo aceite não cria segundo registro. As "
                    + "versões enviadas devem ser as vigentes — o cliente ecoa o que renderizou.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Consentimento registrado (ou já existente)",
                    content = @Content),
            @ApiResponse(responseCode = "400", description = "Aceite incompleto ou versões ausentes",
                    content = @Content),
            @ApiResponse(responseCode = "401", description = "Requisição sem JWT válido",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Usuário não encontrado no tenant atual",
                    content = @Content),
            @ApiResponse(responseCode = "409",
                    description = "Versões declaradas não são as vigentes (CONSENT_VERSION_STALE)",
                    content = @Content)
    })
    public ResponseEntity<Void> registrarConsentimento(@Valid @RequestBody ConsentInputDto input) {
        usuarioService.registerConsent(input);
        return ResponseEntity.ok().build();
    }
}
