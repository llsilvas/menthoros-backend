package com.menthoros.services;

import com.menthoros.dto.strava.StravaWebhookEventDto;
import com.menthoros.entity.Atleta;
import com.menthoros.entity.IntegracaoExterna;
import com.menthoros.entity.TreinoRealizado;
import com.menthoros.enums.FonteDados;
import com.menthoros.enums.StatusSincronizacao;
import com.menthoros.repository.IntegracaoExternaRepository;
import com.menthoros.repository.TreinoRealizadoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StravaWebhookServiceTest {

    @Mock
    private IntegracaoExternaRepository integracaoExternaRepository;
    @Mock
    private TreinoRealizadoRepository treinoRealizadoRepository;
    @Mock
    private StravaActivityService stravaActivityService;

    @AfterEach
    void cleanUp() {
        com.menthoros.multitenancy.TenantContext.clear();
    }

    @Test
    @DisplayName("Deve descartar evento quando owner_id não possui integração ativa")
    void shouldDiscardUnknownOwnerId() {
        when(integracaoExternaRepository.findActiveByExternalAthleteIdAndPlataforma("12345", FonteDados.STRAVA))
                .thenReturn(Optional.empty());

        StravaWebhookService service = new StravaWebhookService(
                integracaoExternaRepository,
                treinoRealizadoRepository,
                stravaActivityService
        );

        StravaWebhookEventDto event = new StravaWebhookEventDto(
                "activity",
                "create",
                999L,
                12345L,
                1710000000L,
                Map.of()
        );

        service.handleEventAsync(event);

        verify(stravaActivityService, never()).syncSingleActivityById(any(), any(), anyLong());
    }

    @Test
    @DisplayName("Deve processar create/update chamando sincronização da atividade")
    void shouldProcessCreateAndUpdateEvents() {
        IntegracaoExterna integracao = mockIntegracao("777");
        when(integracaoExternaRepository.findActiveByExternalAthleteIdAndPlataforma("777", FonteDados.STRAVA))
                .thenReturn(Optional.of(integracao));

        StravaWebhookService service = new StravaWebhookService(
                integracaoExternaRepository,
                treinoRealizadoRepository,
                stravaActivityService
        );

        service.processCreateEvent(1001L, 777L);
        service.processUpdateEvent(1002L, 777L);

        verify(stravaActivityService).syncSingleActivityById(integracao.getAtleta(), integracao, 1001L);
        verify(stravaActivityService).syncSingleActivityById(integracao.getAtleta(), integracao, 1002L);
    }

    @Test
    @DisplayName("Deve marcar treino como CANCELADO em evento delete")
    void shouldMarkTrainingAsCanceledOnDelete() {
        IntegracaoExterna integracao = mockIntegracao("888");
        TreinoRealizado treino = new TreinoRealizado();
        treino.setMetadadosSincronizacao("{\"manual\":false}");

        when(integracaoExternaRepository.findActiveByExternalAthleteIdAndPlataforma("888", FonteDados.STRAVA))
                .thenReturn(Optional.of(integracao));
        when(treinoRealizadoRepository.findByExternalIdAndAtletaId("4242", integracao.getAtleta().getId()))
                .thenReturn(Optional.of(treino));

        StravaWebhookService service = new StravaWebhookService(
                integracaoExternaRepository,
                treinoRealizadoRepository,
                stravaActivityService
        );

        service.processDeleteEvent(4242L, 888L);

        assertEquals(StatusSincronizacao.CANCELADO, treino.getStatusSincronizacao());
        verify(treinoRealizadoRepository).save(treino);
    }

    private IntegracaoExterna mockIntegracao(String externalAthleteId) {
        Atleta atleta = new Atleta();
        atleta.setId(UUID.randomUUID());

        IntegracaoExterna integracao = new IntegracaoExterna();
        integracao.setAtleta(atleta);
        integracao.setTenantId(UUID.randomUUID());
        integracao.setExternalAthleteId(externalAthleteId);
        integracao.setPlataforma(FonteDados.STRAVA);
        integracao.setAtivo(true);
        return integracao;
    }
}
