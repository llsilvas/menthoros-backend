package com.menthoros.controller;

import com.menthoros.entity.Atleta;
import com.menthoros.services.StravaOAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping({"/strava", "/api/strava"})
@Tag(name = "Strava OAuth", description = "Fluxo OAuth2 de conexão com Strava")
public class StravaAuthController {

    private final StravaOAuthService stravaOAuthService;
    @Value("${app.frontend.url:http://localhost:5174}")
    private String frontendUrl;

    @GetMapping("/auth")
    @Operation(summary = "Inicia autorização OAuth2 com Strava")
    public ResponseEntity<Void> startAuth(@RequestParam("atletaId") UUID atletaId) {
        String authorizationUrl = stravaOAuthService.getAuthorizationUrl(atletaId);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, authorizationUrl)
                .build();
    }

    @GetMapping("/auth/url/{atletaId}")
    @Operation(summary = "Retorna URL de autorização OAuth2 do Strava")
    public ResponseEntity<Map<String, String>> getAuthorizationUrl(@PathVariable UUID atletaId) {
        String authorizationUrl = stravaOAuthService.getAuthorizationUrl(atletaId);
        return ResponseEntity.ok(Map.of("authorizationUrl", authorizationUrl));
    }

    @GetMapping("/callback")
    @Operation(summary = "Processa callback OAuth2 do Strava")
    public ResponseEntity<Void> callback(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "error", required = false) String error
    ) {
        if (error != null) {
            log.warn("Erro recebido no callback Strava: {}", error);
            return redirectToFrontend("error");
        }

        if (code == null || state == null) {
            log.warn("Callback Strava inválido: code/state ausentes");
            return redirectToFrontend("error");
        }

        try {
            UUID atletaId = UUID.fromString(state);
            Atleta atleta = stravaOAuthService.findAtletaForCallback(atletaId);
            stravaOAuthService.exchangeCodeForToken(code, atleta);
            return redirectToFrontend("success");
        } catch (Exception ex) {
            log.error("Falha ao processar callback Strava: {}", ex.getMessage(), ex);
            return redirectToFrontend("error");
        }
    }

    @GetMapping("/status/{atletaId}")
    @Operation(summary = "Consulta status de conexão Strava do atleta")
    public ResponseEntity<Map<String, Object>> status(@PathVariable UUID atletaId) {
        return ResponseEntity.ok(stravaOAuthService.getStatus(atletaId));
    }

    @DeleteMapping("/disconnect/{atletaId}")
    @Operation(summary = "Desconecta integração Strava do atleta")
    public ResponseEntity<Void> disconnect(@PathVariable UUID atletaId) {
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
