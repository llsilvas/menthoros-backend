package com.menthoros.controller;

import com.menthoros.entity.IntegracaoExterna;
import com.menthoros.enums.FonteDados;
import com.menthoros.exception.ResourceNotFoundException;
import com.menthoros.exception.StravaRateLimitException;
import com.menthoros.multitenancy.TenantContext;
import com.menthoros.repository.IntegracaoExternaRepository;
import com.menthoros.services.StravaActivityService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StravaActivityControllerTest {

    @Mock
    private StravaActivityService stravaActivityService;

    @Mock
    private IntegracaoExternaRepository integracaoExternaRepository;

    @InjectMocks
    private StravaActivityController controller;

    private UUID tenantId;
    private UUID atletaId;
    private IntegracaoExterna integracao;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        atletaId = UUID.randomUUID();

        integracao = new IntegracaoExterna();
        integracao.setId(UUID.randomUUID());
        integracao.setExternalAthleteId("123456789");
        integracao.setPlataforma(FonteDados.STRAVA);
        integracao.setAtivo(true);
        integracao.setTenantId(tenantId);
        integracao.setUltimaSincronizacao(null);
        integracao.setSyncActivityCount(0);
        integracao.setLastSyncError(null);
        integracao.setCriadoEm(LocalDateTime.now());

        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("getSyncStatus deve retornar dados corretos quando integração existe")
    void getSyncStatusShouldReturnCorrectFormatWhenIntegrationExists() {
        when(integracaoExternaRepository.findByAtletaIdAndPlataformaAndTenantId(
                atletaId, FonteDados.STRAVA, tenantId))
                .thenReturn(Optional.of(integracao));

        ResponseEntity<Map<String, Object>> response = controller.getSyncStatus(atletaId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();

        assertNotNull(body);
        assertEquals(true, body.get("connected"));
        assertEquals(false, body.get("syncing"));
        assertEquals(0, body.get("imported"));
        assertFalse(body.containsKey("lastError"));
        assertFalse(body.containsKey("lastSync"));
        assertEquals("123456789", body.get("externalAthleteId"));
    }

    @Test
    @DisplayName("getSyncStatus deve retornar 404 quando integração não existe")
    void getSyncStatusShouldThrowNotFoundWhenIntegrationDoesNotExist() {
        when(integracaoExternaRepository.findByAtletaIdAndPlataformaAndTenantId(
                atletaId, FonteDados.STRAVA, tenantId))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> {
            controller.getSyncStatus(atletaId);
        });
    }

    @Test
    @DisplayName("sync deve rejeitar chamadas duplicadas (< 30 segundos)")
    void syncShouldRejectDuplicateCallsWithin30Seconds() {
        integracao.setUltimaSincronizacao(Instant.now().minusSeconds(15));

        when(integracaoExternaRepository.findByAtletaIdAndPlataformaAndTenantId(
                atletaId, FonteDados.STRAVA, tenantId))
                .thenReturn(Optional.of(integracao));

        ResponseEntity<Map<String, Object>> response = controller.sync(atletaId);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("Sincronização já em progresso. Aguarde conclusão.", body.get("error"));
        verify(stravaActivityService, never()).syncActivities(any());
    }

    @Test
    @DisplayName("sync deve permitir chamada quando > 30 segundos desde última sincronização")
    void syncShouldAllowCallWhen30SecondsHavePassed() {
        integracao.setUltimaSincronizacao(Instant.now().minusSeconds(31));

        when(integracaoExternaRepository.findByAtletaIdAndPlataformaAndTenantId(
                atletaId, FonteDados.STRAVA, tenantId))
                .thenReturn(Optional.of(integracao));

        when(stravaActivityService.syncActivities(atletaId))
                .thenReturn(45);

        when(integracaoExternaRepository.save(any(IntegracaoExterna.class)))
                .thenReturn(integracao);

        ResponseEntity<Map<String, Object>> response = controller.sync(atletaId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(true, body.get("success"));
        assertEquals(45, body.get("imported"));
        assertEquals("45 atividades importadas com sucesso", body.get("message"));
    }

    @Test
    @DisplayName("sync deve retornar 429 quando atinge rate limit do Strava")
    void syncShouldReturn429OnRateLimit() {
        when(integracaoExternaRepository.findByAtletaIdAndPlataformaAndTenantId(
                atletaId, FonteDados.STRAVA, tenantId))
                .thenReturn(Optional.of(integracao));

        when(stravaActivityService.syncActivities(atletaId))
                .thenThrow(new StravaRateLimitException("Rate limit exceeded"));

        ResponseEntity<Map<String, Object>> response = controller.sync(atletaId);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("Limite de requisições Strava atingido. Tente novamente em alguns minutos.",
                body.get("error"));
        verify(integracaoExternaRepository, never()).save(any());
    }

    @Test
    @DisplayName("sync deve marcar ativo=false quando ocorre erro")
    @SuppressWarnings("null")
    void syncShouldMarkInactiveOnError() {
        when(integracaoExternaRepository.findByAtletaIdAndPlataformaAndTenantId(
                atletaId, FonteDados.STRAVA, tenantId))
                .thenReturn(Optional.of(integracao));

        when(stravaActivityService.syncActivities(atletaId))
                .thenThrow(new RuntimeException("Connection timeout"));

        when(integracaoExternaRepository.save(any(IntegracaoExterna.class)))
                .thenReturn(integracao);

        ResponseEntity<Map<String, Object>> response = controller.sync(atletaId);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertTrue(body.get("error").toString().contains("Falha ao sincronizar"));

        verify(integracaoExternaRepository).save(argThat(arg ->
                !arg.isAtivo() && arg.getLastSyncError() != null));
    }

    @Test
    @DisplayName("sync deve atualizar syncActivityCount e ultimaSincronizacao após sucesso")
    @SuppressWarnings("null")
    void syncShouldUpdateCountAndTimestampOnSuccess() {
        when(integracaoExternaRepository.findByAtletaIdAndPlataformaAndTenantId(
                atletaId, FonteDados.STRAVA, tenantId))
                .thenReturn(Optional.of(integracao));

        when(stravaActivityService.syncActivities(atletaId))
                .thenReturn(90);

        when(integracaoExternaRepository.save(any(IntegracaoExterna.class)))
                .thenReturn(integracao);

        ResponseEntity<Map<String, Object>> response = controller.sync(atletaId);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        verify(integracaoExternaRepository).save(argThat(arg ->
                arg.getSyncActivityCount() == 90 &&
                arg.getUltimaSincronizacao() != null &&
                arg.getLastSyncError() == null));
    }

    @Test
    @DisplayName("getSyncStatus deve indicar syncing=true quando última sync < 1 minuto e ativo=false")
    void getSyncStatusShouldIndicateSyncingWhenRecentAndInactive() {
        integracao.setAtivo(false);
        integracao.setUltimaSincronizacao(Instant.now().minusSeconds(30));

        when(integracaoExternaRepository.findByAtletaIdAndPlataformaAndTenantId(
                atletaId, FonteDados.STRAVA, tenantId))
                .thenReturn(Optional.of(integracao));

        ResponseEntity<Map<String, Object>> response = controller.getSyncStatus(atletaId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(false, body.get("connected"));
        assertEquals(true, body.get("syncing"));
    }

    @Test
    @DisplayName("getSyncStatus deve incluir syncActivityCount quando > 0")
    void getSyncStatusShouldIncludeSyncActivityCount() {
        integracao.setSyncActivityCount(45);

        when(integracaoExternaRepository.findByAtletaIdAndPlataformaAndTenantId(
                atletaId, FonteDados.STRAVA, tenantId))
                .thenReturn(Optional.of(integracao));

        ResponseEntity<Map<String, Object>> response = controller.getSyncStatus(atletaId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(45, body.get("imported"));
    }

    @Test
    @DisplayName("getSyncStatus deve retornar lastError quando houver erro")
    void getSyncStatusShouldIncludeLastErrorWhenPresent() {
        integracao.setLastSyncError("Connection timeout");

        when(integracaoExternaRepository.findByAtletaIdAndPlataformaAndTenantId(
                atletaId, FonteDados.STRAVA, tenantId))
                .thenReturn(Optional.of(integracao));

        ResponseEntity<Map<String, Object>> response = controller.getSyncStatus(atletaId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("Connection timeout", body.get("lastError"));
    }

    @Test
    @DisplayName("sync deve aplicar filtro multi-tenant (TenantContext)")
    void syncShouldApplyMultiTenantFilter() {
        when(integracaoExternaRepository.findByAtletaIdAndPlataformaAndTenantId(
                atletaId, FonteDados.STRAVA, tenantId))
                .thenReturn(Optional.of(integracao));

        when(stravaActivityService.syncActivities(atletaId))
                .thenReturn(50);

        when(integracaoExternaRepository.save(any(IntegracaoExterna.class)))
                .thenReturn(integracao);

        controller.sync(atletaId);

        verify(integracaoExternaRepository).findByAtletaIdAndPlataformaAndTenantId(
                atletaId, FonteDados.STRAVA, tenantId);
    }
}
