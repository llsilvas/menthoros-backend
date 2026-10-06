package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.strava.StravaActivityDto;
import br.com.menthoros.backend.dto.strava.StravaSplitDto;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.EtapaRealizada;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.IntegracaoExternaRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.mapper.TreinoMapper;
import br.com.menthoros.backend.services.helper.AtletaHojeResolver;
import br.com.menthoros.backend.services.impl.StravaActivityServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class StravaActivityServiceTest {

    @Mock
    private AtletaRepository atletaRepository;
    @Mock
    private TreinoRealizadoRepository treinoRealizadoRepository;
    @Mock
    private IntegracaoExternaRepository integracaoExternaRepository;
    @Mock
    private StravaOAuthService stravaOAuthService;
    @Mock
    private WebClient stravaWebClient;

    @Mock
    private TreinoMapper treinoMapper;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private IngestaoTreinoRealizadoService ingestaoTreinoRealizadoService;
    @Mock
    private AtletaHojeResolver hojeResolver;

    @Test
    @DisplayName("Deve mapear atividade Strava para TreinoRealizado com conversões corretas")
    void shouldMapActivityToTreinoRealizado() {
        StravaActivityServiceImpl service = new StravaActivityServiceImpl(
                atletaRepository,
                treinoRealizadoRepository,
                integracaoExternaRepository,
                stravaOAuthService,
                treinoMapper,
                eventPublisher,
                stravaWebClient,
                ingestaoTreinoRealizadoService,
                org.springframework.transaction.support.TransactionOperations.withoutTransaction(), null,
                new br.com.menthoros.backend.config.external.StravaProperties(), hojeResolver
        );

        Atleta atleta = mockAtleta();
        StravaActivityDto activity = new StravaActivityDto(
                999L,
                "Intervalado na pista",
                "Run",
                "2026-04-26T07:30:00Z",
                10_000d,
                3000,
                3300,
                50d,
                3.33d,
                170d,
                185d,
                true,
                70,
                8d,
                "Treino forte",
                false,
                1,
                86d,
                "Garmin Forerunner",
                null,
                List.of()
        );

        TreinoRealizado treino = service.mapToTreinoRealizado(activity, atleta);

        assertEquals("999", treino.getExternalId());
        assertEquals(TipoTreino.PROVA, treino.getTipoTreino());
        assertEquals(new BigDecimal("10.00"), treino.getDistanciaKm());
        assertEquals(Duration.ofSeconds(3000), treino.getDuracaoMin());
        assertEquals(Duration.ofSeconds(300), treino.getPaceMedia());
        assertEquals(170, treino.getFcMedia());
        assertEquals(185, treino.getFcMax());
        assertEquals(172, treino.getCadenciaMedia());
        assertEquals("Garmin Forerunner", treino.getDeviceName());
        assertEquals(70, treino.getSufferScore());
        // startDateLocal "2026-04-26T07:30:00Z" é hora de parede local (Strava mente no "Z") —
        // extrai 2026-04-26 literalmente, sem depender de ZoneId.systemDefault()
        // (fix-treino-registro-fuso-atleta).
        assertEquals(java.time.LocalDate.of(2026, 4, 26), treino.getDataTreino());
    }

    @Test
    @DisplayName("startDateLocal sem sufixo de zona: extrai a data de parede literalmente")
    void parseActivityDateSemSufixoDeZona() {
        StravaActivityServiceImpl service = new StravaActivityServiceImpl(
                atletaRepository, treinoRealizadoRepository, integracaoExternaRepository,
                stravaOAuthService, treinoMapper, eventPublisher, stravaWebClient,
                ingestaoTreinoRealizadoService,
                org.springframework.transaction.support.TransactionOperations.withoutTransaction(), null,
                new br.com.menthoros.backend.config.external.StravaProperties(), hojeResolver
        );
        Atleta atleta = mockAtleta();
        StravaActivityDto activity = new StravaActivityDto(
                1000L, "Noturno", "Run", "2026-10-05T23:45:00",
                5000d, 1500, 1600, 50d, 3.33d, 150d, 165d, true, 50, 6d,
                null, false, null, null, null, null, List.of()
        );

        TreinoRealizado treino = service.mapToTreinoRealizado(activity, atleta);

        assertEquals(java.time.LocalDate.of(2026, 10, 5), treino.getDataTreino());
    }

    @Test
    @DisplayName("startDateLocal ausente: usa hojeResolver.hojeDe(atleta), não LocalDate.now() cru")
    void startDateLocalAusenteUsaHojeDoAtleta() {
        StravaActivityServiceImpl service = new StravaActivityServiceImpl(
                atletaRepository, treinoRealizadoRepository, integracaoExternaRepository,
                stravaOAuthService, treinoMapper, eventPublisher, stravaWebClient,
                ingestaoTreinoRealizadoService,
                org.springframework.transaction.support.TransactionOperations.withoutTransaction(), null,
                new br.com.menthoros.backend.config.external.StravaProperties(), hojeResolver
        );
        Atleta atleta = mockAtleta();
        java.time.LocalDate hojeDoAtleta = java.time.LocalDate.of(2030, 1, 15);
        org.mockito.Mockito.when(hojeResolver.hojeDe(atleta)).thenReturn(hojeDoAtleta);
        StravaActivityDto activity = new StravaActivityDto(
                1001L, "Sem data", "Run", null,
                5000d, 1500, 1600, 50d, 3.33d, 150d, 165d, true, 50, 6d,
                null, false, null, null, null, null, List.of()
        );

        TreinoRealizado treino = service.mapToTreinoRealizado(activity, atleta);

        assertEquals(hojeDoAtleta, treino.getDataTreino());
        org.mockito.Mockito.verify(hojeResolver).hojeDe(atleta);
    }

    @Test
    @DisplayName("Deve mapear lap com conversão de cadência e elevação")
    void shouldMapSplitToEtapaRealizada() {
        StravaActivityServiceImpl service = new StravaActivityServiceImpl(
                atletaRepository,
                treinoRealizadoRepository,
                integracaoExternaRepository,
                stravaOAuthService,
                treinoMapper,
                eventPublisher,
                stravaWebClient,
                ingestaoTreinoRealizadoService,
                org.springframework.transaction.support.TransactionOperations.withoutTransaction(), null,
                new br.com.menthoros.backend.config.external.StravaProperties(), hojeResolver
        );

        StravaSplitDto split = new StravaSplitDto(
                2,
                1000d,
                310,
                300,
                3.33d,
                165d,
                178d,
                85d,
                250d,
                -12d
        );

        EtapaRealizada etapa = service.mapToEtapaRealizada(split);

        assertEquals(2, etapa.getSplitIndex());
        assertEquals(2, etapa.getOrdem());
        assertEquals(new BigDecimal("1.000"), etapa.getDistanciaKm());
        assertEquals(Duration.ofSeconds(300), etapa.getDuracao());
        assertEquals(170, etapa.getCadenciaMedia());
        assertEquals(0, etapa.getElevacaoGanhoMetros());
        assertEquals(12, etapa.getElevacaoPerdaMetros());
    }

    private Atleta mockAtleta() {
        Assessoria assessoria = new Assessoria();
        assessoria.setId(UUID.randomUUID());

        Atleta atleta = new Atleta();
        atleta.setAssessoria(assessoria);
        atleta.setFcLimiar(168);
        return atleta;
    }
}
