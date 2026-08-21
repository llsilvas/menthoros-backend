package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.output.IntervalsIcuAuthorizationUrlDto;
import br.com.menthoros.backend.dto.output.IntervalsIcuConnectionStatusDto;
import br.com.menthoros.backend.services.AtletaProgressService;
import br.com.menthoros.backend.services.IntervalsIcuConnectionService;
import br.com.menthoros.backend.services.IntervalsIcuOAuthService;
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
@RequestMapping("/api/v1/integracoes/me/intervals-icu")
@Tag(name = "intervals-icu", description = "Conexão do atleta com o intervals.icu (push de treinos ao relógio)")
public class IntervalsIcuConnectionController {

    private final IntervalsIcuConnectionService connectionService;
    private final IntervalsIcuOAuthService oauthService;
    private final AtletaProgressService atletaProgressService;

    // O POST de conexão por API key foi removido: OAuth2 substitui o fluxo antigo e não convive
    // com ele (D6). Quem inicia a conexão agora é o GET /authorize-url abaixo.

    // Apenas ROLE_ATLETA, sem ADMIN: o service resolve o atleta por resolverAtletaIdAtual(), que
    // exige um Atleta vinculado ao Usuario autenticado. Um ADMIN sem esse vínculo receberia 404
    // em vez da URL — o contrato anterior descrevia um caminho inalcançável. E é coerente: quem
    // não é atleta não tem conta intervals.icu para conectar.
    @GetMapping("/authorize-url")
    @PreAuthorize("hasRole('ATLETA')")
    @Operation(summary = "URL de consentimento OAuth2 do intervals.icu para o atleta autenticado")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "URL de autorização retornada"),
        @ApiResponse(responseCode = "401", description = "Não autenticado"),
        @ApiResponse(responseCode = "403", description = "Sem papel ATLETA"),
        @ApiResponse(responseCode = "404", description = "Usuário autenticado sem atleta vinculado")
    })
    public ResponseEntity<IntervalsIcuAuthorizationUrlDto> authorizeUrl() {
        return ResponseEntity.ok(new IntervalsIcuAuthorizationUrlDto(oauthService.getAuthorizationUrl()));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ATLETA','ADMIN')")
    @Operation(summary = "Status da conexão intervals.icu do atleta (nunca retorna a key)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Status retornado"),
        @ApiResponse(responseCode = "404", description = "Atleta nunca conectou"),
        @ApiResponse(responseCode = "401", description = "Não autenticado"),
        @ApiResponse(responseCode = "403", description = "Sem papel ATLETA/ADMIN")
    })
    public ResponseEntity<IntervalsIcuConnectionStatusDto> status() {
        var atletaId = atletaProgressService.resolverAtletaIdAtual();
        return connectionService.status(atletaId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping
    @PreAuthorize("hasAnyRole('ATLETA','ADMIN')")
    @Operation(summary = "Revoga o acesso no intervals.icu e desconecta a conta")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Desconectado"),
        @ApiResponse(responseCode = "401", description = "Não autenticado"),
        @ApiResponse(responseCode = "403", description = "Sem papel ATLETA/ADMIN")
    })
    public ResponseEntity<Void> desconectar() {
        var atletaId = atletaProgressService.resolverAtletaIdAtual();
        // Passa pelo OAuth service para revogar no provedor antes do soft-disconnect local (D7).
        oauthService.revogarEDesconectar(atletaId);
        return ResponseEntity.noContent().build();
    }
}
