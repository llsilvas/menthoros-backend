package com.menthoros.controller;

import com.menthoros.services.StravaActivityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/strava")
@Tag(name = "Strava Sync", description = "Sincronização de atividades do Strava")
public class StravaActivityController {

    private final StravaActivityService stravaActivityService;

    @PostMapping("/sync/{atletaId}")
    @Operation(summary = "Dispara sincronização manual de atividades do atleta")
    public ResponseEntity<Map<String, Object>> sync(@PathVariable UUID atletaId) {
        int imported = stravaActivityService.syncActivities(atletaId);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "imported", imported
        ));
    }
}
