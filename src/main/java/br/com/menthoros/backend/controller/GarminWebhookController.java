package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.config.external.GarminProperties;
import br.com.menthoros.backend.services.GarminWebhookService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/garmin/webhook")
@Tag(name = "garmin-webhook", description = "Pings do Garmin (desregistro e mudança de permissões)")
public class GarminWebhookController {

    private static final TypeReference<Map<String, List<JsonNode>>> TIPO_CORPO = new TypeReference<>() {};

    private final GarminProperties properties;
    private final GarminWebhookService webhookService;
    private final ObjectMapper objectMapper;

    // Sem @PreAuthorize: público por config (garminPaths); a autenticação é o segredo no caminho.
    // O corpo entra como String e só é interpretado depois do segredo: com @RequestBody tipado, um
    // POST malformado devolveria 400/415 antes da checagem e revelaria que o endpoint existe. O
    // tamanho é limitado pelo PublicRequestSizeLimitFilter.
    @PostMapping("/{segredo}")
    @Operation(summary = "Recebe pings do Garmin")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Processado (inclusive userId ou chave desconhecidos)"),
        @ApiResponse(responseCode = "404", description = "Segredo errado ou webhook não configurado"),
        @ApiResponse(responseCode = "413", description = "Corpo acima do limite")
    })
    public ResponseEntity<Void> receber(
            @Parameter(description = "Segredo do webhook") @PathVariable String segredo,
            @RequestBody(required = false) String corpoBruto) {
        if (!properties.webhookConfigurado() || !MessageDigest.isEqual(
                properties.getWebhookSecret().getBytes(StandardCharsets.UTF_8), segredo.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.notFound().build();
        }
        webhookService.processar(interpretar(corpoBruto));
        return ResponseEntity.ok().build();
    }

    /** Corpo ilegível não é erro do Garmin que valha 5xx (reenvio): vira lote vazio, sem logar o conteúdo. */
    private Map<String, List<JsonNode>> interpretar(String corpoBruto) {
        if (corpoBruto == null || corpoBruto.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(corpoBruto, TIPO_CORPO);
        } catch (JsonProcessingException e) {
            log.warn("Webhook Garmin com corpo ilegível ({} bytes); ignorado", corpoBruto.length());
            return Map.of();
        }
    }
}
