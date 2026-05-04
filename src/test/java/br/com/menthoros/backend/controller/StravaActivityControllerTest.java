package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.output.StravaSyncResponseDto;
import br.com.menthoros.backend.dto.output.StravaSyncStatusDto;
import br.com.menthoros.backend.exception.DuplicateResourceException;
import br.com.menthoros.backend.exception.ResourceNotFoundException;
import br.com.menthoros.backend.exception.StravaRateLimitException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.services.StravaActivityService;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StravaActivityControllerTest {

    @Mock
    private StravaActivityService stravaActivityService;

    @InjectMocks
    private StravaActivityController controller;

    private UUID tenantId;
    private UUID atletaId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        atletaId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("getSyncStatus deve retornar StravaSyncStatusDto quando integração existe")
    void getSyncStatusShouldReturnStatusDtoWhenIntegrationExists() {
        StravaSyncStatusDto statusDto = new StravaSyncStatusDto(
                true, false, 0, null, null, "123456789"
        );

        when(stravaActivityService.getSyncStatus(atletaId, tenantId))
                .thenReturn(statusDto);

        ResponseEntity<StravaSyncStatusDto> response = controller.getSyncStatus(atletaId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(true, response.getBody().connected());
        assertEquals(false, response.getBody().syncing());
        assertEquals(0, response.getBody().imported());
        assertEquals("123456789", response.getBody().externalAthleteId());
    }

    @Test
    @DisplayName("getSyncStatus deve lançar ResourceNotFoundException quando integração não existe")
    void getSyncStatusShouldThrowNotFoundWhenIntegrationDoesNotExist() {
        when(stravaActivityService.getSyncStatus(atletaId, tenantId))
                .thenThrow(new ResourceNotFoundException("Integração Strava não encontrada"));

        assertThrows(ResourceNotFoundException.class, () -> {
            controller.getSyncStatus(atletaId);
        });
    }

    @Test
    @DisplayName("sync deve retornar StravaSyncResponseDto quando sincronização sucede")
    void syncShouldReturnResponseDtoOnSuccess() {
        StravaSyncResponseDto responseDto = new StravaSyncResponseDto(45, "45 atividades importadas com sucesso");

        when(stravaActivityService.syncActivitiesForAtleta(atletaId, tenantId))
                .thenReturn(responseDto);

        ResponseEntity<StravaSyncResponseDto> response = controller.sync(atletaId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(45, response.getBody().imported());
        assertEquals("45 atividades importadas com sucesso", response.getBody().message());
    }

    @Test
    @DisplayName("sync deve lançar DuplicateResourceException quando sincronização já em progresso")
    void syncShouldThrowDuplicateWhenAlreadyInProgress() {
        when(stravaActivityService.syncActivitiesForAtleta(atletaId, tenantId))
                .thenThrow(new DuplicateResourceException("Sincronização já em progresso. Aguarde conclusão."));

        assertThrows(DuplicateResourceException.class, () -> {
            controller.sync(atletaId);
        });
    }

    @Test
    @DisplayName("sync deve lançar StravaRateLimitException quando atinge rate limit")
    void syncShouldThrowRateLimitException() {
        when(stravaActivityService.syncActivitiesForAtleta(atletaId, tenantId))
                .thenThrow(new StravaRateLimitException("Limite de requisições Strava atingido"));

        assertThrows(StravaRateLimitException.class, () -> {
            controller.sync(atletaId);
        });
    }

    @Test
    @DisplayName("sync deve lançar ResourceNotFoundException quando atleta sem integração")
    void syncShouldThrowNotFoundWhenNoIntegration() {
        when(stravaActivityService.syncActivitiesForAtleta(atletaId, tenantId))
                .thenThrow(new ResourceNotFoundException("Atleta sem integração Strava ativa"));

        assertThrows(ResourceNotFoundException.class, () -> {
            controller.sync(atletaId);
        });
    }

    @Test
    @DisplayName("getSyncStatus deve indicar syncing=true quando sincronização em progresso")
    void getSyncStatusShouldIndicateSyncingWhenInProgress() {
        StravaSyncStatusDto statusDto = new StravaSyncStatusDto(
                false, true, 0, null, Instant.now().minusSeconds(30), "123456789"
        );

        when(stravaActivityService.getSyncStatus(atletaId, tenantId))
                .thenReturn(statusDto);

        ResponseEntity<StravaSyncStatusDto> response = controller.getSyncStatus(atletaId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(false, response.getBody().connected());
        assertEquals(true, response.getBody().syncing());
    }

    @Test
    @DisplayName("getSyncStatus deve incluir lastError quando houver erro")
    void getSyncStatusShouldIncludeLastErrorWhenPresent() {
        StravaSyncStatusDto statusDto = new StravaSyncStatusDto(
                true, false, 10, "Connection timeout", Instant.now(), "123456789"
        );

        when(stravaActivityService.getSyncStatus(atletaId, tenantId))
                .thenReturn(statusDto);

        ResponseEntity<StravaSyncStatusDto> response = controller.getSyncStatus(atletaId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Connection timeout", response.getBody().lastError());
    }

    @Test
    @DisplayName("sync deve aplicar filtro multi-tenant (TenantContext)")
    void syncShouldApplyMultiTenantFilter() {
        StravaSyncResponseDto responseDto = new StravaSyncResponseDto(50, "50 atividades importadas com sucesso");

        when(stravaActivityService.syncActivitiesForAtleta(atletaId, tenantId))
                .thenReturn(responseDto);

        controller.sync(atletaId);

        verify(stravaActivityService).syncActivitiesForAtleta(atletaId, tenantId);
    }

    @Test
    @DisplayName("getSyncStatus deve aplicar filtro multi-tenant (TenantContext)")
    void getSyncStatusShouldApplyMultiTenantFilter() {
        StravaSyncStatusDto statusDto = new StravaSyncStatusDto(
                true, false, 5, null, Instant.now(), "123456789"
        );

        when(stravaActivityService.getSyncStatus(atletaId, tenantId))
                .thenReturn(statusDto);

        controller.getSyncStatus(atletaId);

        verify(stravaActivityService).getSyncStatus(atletaId, tenantId);
    }
}
