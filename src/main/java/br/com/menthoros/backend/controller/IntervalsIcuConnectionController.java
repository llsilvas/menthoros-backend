package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.output.IntervalsIcuConnectionStatusDto;
import br.com.menthoros.backend.services.AtletaProgressService;
import br.com.menthoros.backend.services.IntervalsIcuConnectionService;
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
    private final AtletaProgressService atletaProgressService;

    // O POST de conexão por API key foi removido: OAuth2 substitui o fluxo antigo e não convive
    // com ele (D6). O endpoint de autorização entra no Bloco 5 como GET /authorize-url.

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
    @Operation(summary = "Desconecta a conta intervals.icu (soft — pushes futuros ficam inativos)")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Desconectado"),
        @ApiResponse(responseCode = "401", description = "Não autenticado"),
        @ApiResponse(responseCode = "403", description = "Sem papel ATLETA/ADMIN")
    })
    public ResponseEntity<Void> desconectar() {
        var atletaId = atletaProgressService.resolverAtletaIdAtual();
        connectionService.desconectar(atletaId);
        return ResponseEntity.noContent().build();
    }
}
