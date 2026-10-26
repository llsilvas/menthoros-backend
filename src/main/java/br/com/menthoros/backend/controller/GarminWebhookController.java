package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.config.external.GarminProperties;
import br.com.menthoros.backend.services.GarminWebhookService;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

/**
 * Webhook do Garmin (D6). O Garmin só aceita URL, então o segredo vai no caminho. Segredo errado
 * ou não configurado responde 404 (não revela que o endpoint existe). Nada aqui loga o corpo, e o
 * {@code StructuredLoggingFilter} mascara o segredo na URI.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/garmin/webhook")
@Tag(name = "garmin-webhook", description = "Pings do Garmin (desregistro e mudança de permissões)")
public class GarminWebhookController {

    private final GarminProperties properties;
    private final GarminWebhookService webhookService;

    // Sem @PreAuthorize: público por config (garminPaths); a autenticação é o segredo no caminho.
    @PostMapping("/{segredo}")
    @Operation(summary = "Recebe pings do Garmin")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Processado (inclusive userId ou chave desconhecidos)"),
        @ApiResponse(responseCode = "404", description = "Segredo errado ou webhook não configurado")
    })
    public ResponseEntity<Void> receber(
            @Parameter(description = "Segredo do webhook") @PathVariable String segredo,
            @RequestBody(required = false) Map<String, List<JsonNode>> corpo) {
        String esperado = properties.getWebhookSecret();
        if (esperado == null || esperado.isBlank() || !MessageDigest.isEqual(
                esperado.getBytes(StandardCharsets.UTF_8), segredo.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.notFound().build();
        }
        webhookService.processar(corpo);
        return ResponseEntity.ok().build();
    }
}
