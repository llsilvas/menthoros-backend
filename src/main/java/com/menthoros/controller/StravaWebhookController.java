package com.menthoros.controller;

import com.menthoros.config.StravaProperties;
import com.menthoros.dto.strava.StravaWebhookEventDto;
import com.menthoros.services.StravaWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/strava/webhook")
@Tag(name = "Strava Webhook", description = "Recebimento de eventos do webhook do Strava")
public class StravaWebhookController {

    private final StravaProperties stravaProperties;
    private final StravaWebhookService stravaWebhookService;

    @GetMapping
    @Operation(summary = "Valida subscription do webhook Strava")
    public ResponseEntity<Map<String, String>> validateSubscription(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String verifyToken,
            @RequestParam("hub.challenge") String challenge
    ) {
        if (!"subscribe".equalsIgnoreCase(mode)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        if (!verifyToken.equals(stravaProperties.getWebhookVerifyToken())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok(Map.of("hub.challenge", challenge));
    }

    @PostMapping
    @Operation(summary = "Recebe eventos do webhook Strava")
    public ResponseEntity<Void> receiveEvent(@RequestBody StravaWebhookEventDto event) {
        stravaWebhookService.handleEventAsync(event);
        return ResponseEntity.ok().build();
    }
}
