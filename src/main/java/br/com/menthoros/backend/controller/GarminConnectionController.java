package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.output.GarminAuthorizationUrlDto;
import br.com.menthoros.backend.dto.output.GarminConnectionStatusDto;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.services.AtletaProgressService;
import br.com.menthoros.backend.services.GarminConnectionService;
import br.com.menthoros.backend.services.GarminOAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Sem @RequireTenant: endpoints /me resolvem o atletaId do JWT via resolverAtletaIdAtual(),
// sem receber resource-ID; isolamento por TenantContext + queries tenant-scoped.
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/integracoes/me/garmin")
@Tag(name = "garmin-connection", description = "Conexão do atleta com o Garmin (canal direto)")
public class GarminConnectionController {

    private final GarminConnectionService connectionService;
    private final GarminOAuthService oauthService;
    private final AtletaProgressService atletaProgressService;

    // Só ATLETA: resolverAtletaIdAtual() exige atleta vinculado ao usuário (mesmo contrato do intervals.icu).
    @GetMapping("/authorize-url")
    @PreAuthorize("hasRole('ATLETA')")
    @Operation(summary = "URL de consentimento OAuth2 do Garmin para o atleta autenticado")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "URL de autorização retornada"),
        @ApiResponse(responseCode = "401", description = "Não autenticado"),
        @ApiResponse(responseCode = "403", description = "Sem papel ATLETA"),
        @ApiResponse(responseCode = "404", description = "Usuário autenticado sem atleta vinculado"),
        @ApiResponse(responseCode = "503", description = "Canal Garmin desligado ou sem credenciais neste ambiente")
    })
    public ResponseEntity<GarminAuthorizationUrlDto> authorizeUrl() {
        var atletaId = atletaProgressService.resolverAtletaIdAtual();
        return ResponseEntity.ok(new GarminAuthorizationUrlDto(oauthService.gerarAuthorizeUrl(atletaId)));
    }

    // Status e delete não dependem da flag: desconectar não pode ficar preso a ela (D11).
    @GetMapping
    @PreAuthorize("hasAnyRole('ATLETA','ADMIN')")
    @Operation(summary = "Status da conexão Garmin do atleta (nunca retorna token)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Status retornado"),
        @ApiResponse(responseCode = "404", description = "Atleta nunca conectou"),
        @ApiResponse(responseCode = "401", description = "Não autenticado"),
        @ApiResponse(responseCode = "403", description = "Sem papel ATLETA/ADMIN")
    })
    public ResponseEntity<GarminConnectionStatusDto> status() {
        var atletaId = atletaProgressService.resolverAtletaIdAtual();
        return connectionService.status(atletaId, TenantContext.getRequiredTenantId())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping
    @PreAuthorize("hasAnyRole('ATLETA','ADMIN')")
    @Operation(summary = "Remove o registro no Garmin e desconecta a conta")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Desconectado"),
        @ApiResponse(responseCode = "401", description = "Não autenticado"),
        @ApiResponse(responseCode = "403", description = "Sem papel ATLETA/ADMIN")
    })
    public ResponseEntity<Void> desconectar() {
        var atletaId = atletaProgressService.resolverAtletaIdAtual();
        connectionService.desconectar(atletaId, TenantContext.getRequiredTenantId());
        return ResponseEntity.noContent().build();
    }
}
