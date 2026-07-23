package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.config.asaas.AsaasProperties;
import br.com.menthoros.backend.dto.asaas.AsaasWebhookEventDto;
import br.com.menthoros.backend.services.AsaasWebhookEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Webhook público do Asaas ({@code /api/v1/asaas/webhook}) — sem JWT (path em {@code permitAll},
 * mesmo padrão do {@code StravaWebhookController}). Autentica pelo header {@code asaas-access-token}
 * (CA11) antes de qualquer processamento; a idempotência e as transições ficam no serviço.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/asaas/webhook")
@Tag(name = "asaas-webhook", description = "Recebimento de eventos de cobrança do Asaas")
public class AsaasWebhookController {

    private final AsaasProperties asaasProperties;
    private final AsaasWebhookEventService asaasWebhookEventService;

    @PostMapping
    @Operation(summary = "Recebe eventos do webhook do Asaas")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Evento recebido/processado"),
            @ApiResponse(responseCode = "401", description = "Header asaas-access-token ausente ou inválido")
    })
    public ResponseEntity<Void> receiveEvent(
            @RequestHeader(value = "asaas-access-token", required = false) String accessToken,
            @RequestBody AsaasWebhookEventDto event) {
        if (!tokenValido(accessToken)) {
            log.warn("[asaas-webhook] requisição rejeitada: header asaas-access-token ausente ou inválido");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        asaasWebhookEventService.processar(event);
        return ResponseEntity.ok().build();
    }

    /** Comparação constant-time; rejeita se o token configurado estiver ausente (misconfiguração). */
    private boolean tokenValido(String recebido) {
        String esperado = asaasProperties.getWebhook().getAccessToken();
        if (esperado == null || esperado.isBlank() || recebido == null) {
            return false;
        }
        return MessageDigest.isEqual(
                recebido.getBytes(StandardCharsets.UTF_8),
                esperado.getBytes(StandardCharsets.UTF_8));
    }
}
