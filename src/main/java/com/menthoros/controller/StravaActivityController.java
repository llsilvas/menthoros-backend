package com.menthoros.controller;

import com.menthoros.entity.IntegracaoExterna;
import com.menthoros.enums.FonteDados;
import com.menthoros.exception.ResourceNotFoundException;
import com.menthoros.exception.StravaRateLimitException;
import com.menthoros.multitenancy.TenantContext;
import com.menthoros.repository.IntegracaoExternaRepository;
import com.menthoros.services.StravaActivityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/strava")
@Tag(name = "Strava Sync", description = "Sincronização de atividades do Strava")
public class StravaActivityController {

    private final StravaActivityService stravaActivityService;
    private final IntegracaoExternaRepository integracaoExternaRepository;

    @PostMapping("/sync/{atletaId}")
    @Operation(summary = "Dispara sincronização manual de atividades do atleta")
    public ResponseEntity<Map<String, Object>> sync(@PathVariable UUID atletaId) {
        UUID tenantId = TenantContext.getRequiredTenantId();

        IntegracaoExterna integracao = integracaoExternaRepository
                .findByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.STRAVA, tenantId)
                .orElseThrow(() -> new IllegalStateException("Atleta sem integração Strava ativa"));

        if (isRecentlySynced(integracao)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "error", "Sincronização já em progresso. Aguarde conclusão."
            ));
        }

        try {
            int imported = stravaActivityService.syncActivities(atletaId);
            integracao.setSyncActivityCount(imported);
            integracao.setUltimaSincronizacao(Instant.now());
            integracao.setLastSyncError(null);
            integracaoExternaRepository.save(integracao);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "imported", imported,
                    "message", imported + " atividades importadas com sucesso"
            ));
        } catch (StravaRateLimitException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                    "error", "Limite de requisições Strava atingido. Tente novamente em alguns minutos."
            ));
        } catch (Exception e) {
            integracao.setAtivo(false);
            integracao.setLastSyncError(e.getMessage());
            integracaoExternaRepository.save(integracao);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "Falha ao sincronizar: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/sync-status/{atletaId}")
    @Operation(summary = "Retorna status da última/atual sincronização")
    public ResponseEntity<Map<String, Object>> getSyncStatus(@PathVariable UUID atletaId) {
        UUID tenantId = TenantContext.getRequiredTenantId();

        IntegracaoExterna integracao = integracaoExternaRepository
                .findByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.STRAVA, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Integração Strava não encontrada"));

        boolean syncing = isSyncing(integracao);

        Map<String, Object> response = new java.util.HashMap<>();
        response.put("connected", integracao.isAtivo());
        response.put("syncing", syncing);
        response.put("imported", integracao.getSyncActivityCount() != null ?
                integracao.getSyncActivityCount() : 0);
        if (integracao.getLastSyncError() != null) {
            response.put("lastError", integracao.getLastSyncError());
        }
        if (integracao.getUltimaSincronizacao() != null) {
            response.put("lastSync", integracao.getUltimaSincronizacao());
        }
        if (integracao.getExternalAthleteId() != null) {
            response.put("externalAthleteId", integracao.getExternalAthleteId());
        }

        return ResponseEntity.ok(response);
    }

    private boolean isSyncing(IntegracaoExterna integracao) {
        if (integracao.getUltimaSincronizacao() == null) {
            return false;
        }

        Instant oneMinuteAgo = Instant.now().minus(Duration.ofMinutes(1));
        return oneMinuteAgo.isBefore(integracao.getUltimaSincronizacao()) && !integracao.isAtivo();
    }

    private boolean isRecentlySynced(IntegracaoExterna integracao) {
        if (integracao.getUltimaSincronizacao() == null) {
            return false;
        }

        Instant thirtySecondsAgo = Instant.now().minus(Duration.ofSeconds(30));
        return thirtySecondsAgo.isBefore(integracao.getUltimaSincronizacao());
    }
}
