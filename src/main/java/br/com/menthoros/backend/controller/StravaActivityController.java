package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.output.StravaSyncResponseDto;
import br.com.menthoros.backend.dto.output.StravaSyncStatusDto;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.services.StravaActivityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/strava")
@Tag(name = "Strava Sync", description = "Sincronização de atividades do Strava")
public class StravaActivityController {

    private final StravaActivityService stravaActivityService;

    @PostMapping("/sync/{atletaId}")
    @Operation(summary = "Dispara sincronização manual de atividades do atleta")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Sincronização concluída"),
        @ApiResponse(responseCode = "404", description = "Integração Strava não encontrada para o atleta"),
        @ApiResponse(responseCode = "409", description = "Sincronização já em progresso"),
        @ApiResponse(responseCode = "429", description = "Limite de requisições Strava atingido")
    })
    public ResponseEntity<StravaSyncResponseDto> sync(@PathVariable UUID atletaId) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        return ResponseEntity.ok(stravaActivityService.syncActivitiesForAtleta(atletaId, tenantId));
    }

    @GetMapping("/sync-status/{atletaId}")
    @Operation(summary = "Retorna status da última/atual sincronização")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Status retornado com sucesso"),
        @ApiResponse(responseCode = "404", description = "Integração Strava não encontrada"),
        @ApiResponse(responseCode = "401", description = "Não autenticado")
    })
    public ResponseEntity<StravaSyncStatusDto> getSyncStatus(@Parameter(description = "ID único do atleta") @PathVariable UUID atletaId) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        return ResponseEntity.ok(stravaActivityService.getSyncStatus(atletaId, tenantId));
    }
}
