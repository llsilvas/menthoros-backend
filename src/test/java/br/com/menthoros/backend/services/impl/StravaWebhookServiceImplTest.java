package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.repository.IntegracaoExternaRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.IngestaoTreinoRealizadoService;
import br.com.menthoros.backend.services.StravaActivityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StravaWebhookServiceImplTest {

    @Mock
    private IntegracaoExternaRepository integracaoExternaRepository;
    @Mock
    private TreinoRealizadoRepository treinoRealizadoRepository;
    @Mock
    private StravaActivityService stravaActivityService;
    @Mock
    private IngestaoTreinoRealizadoService ingestaoTreinoRealizadoService;

    private StravaWebhookServiceImpl service;
    private final Long ownerId = 641775L;
    private final Long objectId = 999L;

    @BeforeEach
    void setUp() {
        service = new StravaWebhookServiceImpl(integracaoExternaRepository, treinoRealizadoRepository, stravaActivityService, ingestaoTreinoRealizadoService);
    }

    @Nested
    @DisplayName("processCreateEvent / processUpdateEvent — guard autoSyncPausado (D5.2, CA10)")
    class GuardAutoSyncPausado {

        @Test
        @DisplayName("autoSyncPausado=true: processCreateEvent NÃO chama syncSingleActivityById, sem exceção")
        void createEventPuladoQuandoPausado() {
            when(integracaoExternaRepository.findActiveByExternalAthleteIdAndPlataforma(String.valueOf(ownerId), FonteDados.STRAVA))
                    .thenReturn(Optional.of(integracao(true)));

            service.processCreateEvent(objectId, ownerId);

            verify(stravaActivityService, never()).syncSingleActivityById(any(), any(), anyLong());
        }

        @Test
        @DisplayName("autoSyncPausado=true: processUpdateEvent NÃO chama syncSingleActivityById, sem exceção")
        void updateEventPuladoQuandoPausado() {
            when(integracaoExternaRepository.findActiveByExternalAthleteIdAndPlataforma(String.valueOf(ownerId), FonteDados.STRAVA))
                    .thenReturn(Optional.of(integracao(true)));

            service.processUpdateEvent(objectId, ownerId);

            verify(stravaActivityService, never()).syncSingleActivityById(any(), any(), anyLong());
        }

        @Test
        @DisplayName("autoSyncPausado=false: processCreateEvent chama syncSingleActivityById normalmente")
        void createEventProcessadoQuandoNaoPausado() {
            IntegracaoExterna integracao = integracao(false);
            when(integracaoExternaRepository.findActiveByExternalAthleteIdAndPlataforma(String.valueOf(ownerId), FonteDados.STRAVA))
                    .thenReturn(Optional.of(integracao));

            service.processCreateEvent(objectId, ownerId);

            verify(stravaActivityService).syncSingleActivityById(integracao.getAtleta(), integracao, objectId);
        }

        @Test
        @DisplayName("autoSyncPausado=false: processUpdateEvent chama syncSingleActivityById normalmente")
        void updateEventProcessadoQuandoNaoPausado() {
            IntegracaoExterna integracao = integracao(false);
            when(integracaoExternaRepository.findActiveByExternalAthleteIdAndPlataforma(String.valueOf(ownerId), FonteDados.STRAVA))
                    .thenReturn(Optional.of(integracao));

            service.processUpdateEvent(objectId, ownerId);

            verify(stravaActivityService).syncSingleActivityById(integracao.getAtleta(), integracao, objectId);
        }
    }

    private IntegracaoExterna integracao(boolean autoSyncPausado) {
        Atleta atleta = new Atleta();
        atleta.setId(UUID.randomUUID());

        IntegracaoExterna integracao = new IntegracaoExterna();
        integracao.setAtleta(atleta);
        integracao.setPlataforma(FonteDados.STRAVA);
        integracao.setAtivo(true);
        integracao.setAutoSyncPausado(autoSyncPausado);
        return integracao;
    }
}
