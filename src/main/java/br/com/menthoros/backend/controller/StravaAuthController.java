package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.services.StravaOAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/strava")
@Tag(name = "strava", description = "Fluxo OAuth2 de conexão com Strava")
public class StravaAuthController {

    private final StravaOAuthService stravaOAuthService;
    @Value("${app.frontend.url:http://localhost:5174}")
    private String frontendUrl;

    @GetMapping("/auth")
    @Operation(summary = "Inicia autorização OAuth2 com Strava")
    @ApiResponses({
        @ApiResponse(responseCode = "302", description = "Redirecionamento para Strava OAuth"),
        @ApiResponse(responseCode = "401", description = "Não autenticado")
    })
    public ResponseEntity<Void> startAuth(@Parameter(description = "ID do atleta para iniciar autenticação Strava") @RequestParam("atletaId") UUID atletaId) {
        String authorizationUrl = stravaOAuthService.getAuthorizationUrl(atletaId);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, authorizationUrl)
                .build();
    }

    @GetMapping("/auth/url/{atletaId}")
    @Operation(summary = "Retorna URL de autorização OAuth2 do Strava")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "URL de autorização retornada com sucesso"),
        @ApiResponse(responseCode = "401", description = "Não autenticado")
    })
    public ResponseEntity<Map<String, String>> getAuthorizationUrl(@Parameter(description = "ID único do atleta") @PathVariable UUID atletaId) {
        String authorizationUrl = stravaOAuthService.getAuthorizationUrl(atletaId);
        return ResponseEntity.ok(Map.of("authorizationUrl", authorizationUrl));
    }

    @GetMapping("/callback")
    @Operation(summary = "Processa callback OAuth2 do Strava")
    @ApiResponses({
        @ApiResponse(responseCode = "302", description = "Redirecionamento para frontend com status"),
        @ApiResponse(responseCode = "400", description = "Parâmetros inválidos no callback"),
        @ApiResponse(responseCode = "401", description = "Falha na autenticação com Strava")
    })
    public ResponseEntity<Void> callback(
            @Parameter(description = "Código de autorização do OAuth2 fornecido por Strava") @RequestParam(value = "code", required = false) String code,
            @Parameter(description = "Token de estado para validação CSRF (UUID do atleta)") @RequestParam(value = "state", required = false) String state,
            @Parameter(description = "Mensagem de erro retornada pelo Strava em caso de negação de acesso") @RequestParam(value = "error", required = false) String error
    ) {
        if (error != null) {
            log.warn("Erro recebido no callback Strava: {}", error);
            return redirectToFrontend("error");
        }

        if (code == null || state == null) {
            log.warn("Callback Strava inválido: code/state ausentes");
            return redirectToFrontend("error");
        }

        UUID atletaId = UUID.fromString(state);
        Atleta atleta = stravaOAuthService.findAtletaForCallback(atletaId);
        stravaOAuthService.exchangeCodeForToken(code, atleta);
        return redirectToFrontend("success");
    }

    @GetMapping("/status/{atletaId}")
    @Operation(summary = "Consulta status de conexão Strava do atleta")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Status de conexão retornado com sucesso"),
        @ApiResponse(responseCode = "404", description = "Atleta não encontrado"),
        @ApiResponse(responseCode = "401", description = "Não autenticado")
    })
    public ResponseEntity<Map<String, Object>> status(@Parameter(description = "ID único do atleta") @PathVariable UUID atletaId) {
        return ResponseEntity.ok(stravaOAuthService.getStatus(atletaId));
    }

    @DeleteMapping("/disconnect/{atletaId}")
    @Operation(summary = "Desconecta integração Strava do atleta")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Desconexão executada com sucesso"),
        @ApiResponse(responseCode = "404", description = "Atleta não encontrado"),
        @ApiResponse(responseCode = "401", description = "Não autenticado")
    })
    public ResponseEntity<Void> disconnect(@Parameter(description = "ID único do atleta") @PathVariable UUID atletaId) {
        stravaOAuthService.disconnect(atletaId);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<Void> redirectToFrontend(String status) {
        URI redirectUri = UriComponentsBuilder
                .fromUriString(frontendUrl)
                .queryParam("strava", status)
                .build()
                .toUri();

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, redirectUri.toString())
                .build();
    }
}
