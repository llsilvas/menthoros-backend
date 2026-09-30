package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.strava.StravaActivityDto;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.mapper.TreinoMapper;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.IntegracaoExternaRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.helper.TreinoDedupHelper;
import br.com.menthoros.backend.services.impl.StravaActivityServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@code syncSingleActivityById} delega dedup/TSS/evento/carga para
 * {@link IngestaoTreinoRealizadoService} (ingestao-treino-realizado, task 5.3) — antes chamava
 * {@code TreinoDedupHelper}/{@code TsbService} direto e nunca publicava evento para este caminho.
 */
@ExtendWith(MockitoExtension.class)
class StravaActivityServiceImplSyncTest {

    @Mock private AtletaRepository atletaRepository;
    @Mock private TreinoRealizadoRepository treinoRealizadoRepository;
    @Mock private IntegracaoExternaRepository integracaoExternaRepository;
    @Mock private StravaOAuthService stravaOAuthService;
    @Mock private TreinoMapper treinoMapper;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private WebClient stravaWebClient;
    @Mock private IngestaoTreinoRealizadoService ingestaoTreinoRealizadoService;

    @Mock private WebClient.RequestHeadersUriSpec<?> uriSpec;
    @Mock private WebClient.RequestHeadersSpec<?> headersSpec;
    @Mock private WebClient.ResponseSpec responseSpec;

    private StravaActivityServiceImpl service;
    private Atleta atleta;

    @BeforeEach
    void setUp() {
        service = new StravaActivityServiceImpl(
                atletaRepository, treinoRealizadoRepository, integracaoExternaRepository,
                stravaOAuthService, treinoMapper, eventPublisher, stravaWebClient,
                ingestaoTreinoRealizadoService,
                org.springframework.transaction.support.TransactionOperations.withoutTransaction(), null, new br.com.menthoros.backend.config.external.StravaProperties());

        Assessoria assessoria = new Assessoria();
        assessoria.setId(UUID.randomUUID());
        atleta = new Atleta();
        atleta.setId(UUID.randomUUID());
        atleta.setAssessoria(assessoria);

        when(stravaOAuthService.getValidToken(atleta.getId())).thenReturn("token-valido");
    }

    @Nested
    @DisplayName("syncSingleActivityById")
    class SyncSingleActivityById {

        @Test
        @DisplayName("atividade nova: find-or-new sem id, registrar recebe entidade transiente com externalId")
        void atividadeNovaChamaRegistrarComEntidadeTransiente() {
            IntegracaoExterna integracao = new IntegracaoExterna();
            StravaActivityDto activity = stravaActivity(555L);
            stubStravaCall(activity);
            when(treinoRealizadoRepository.findByExternalIdAndAtletaId("555", atleta.getId()))
                    .thenReturn(Optional.empty());
            stubLapsVazias();
            when(ingestaoTreinoRealizadoService.registrar(any(), anyString()))
                    .thenAnswer(inv -> new TreinoDedupHelper.SaveResult(inv.getArgument(0), true));

            service.syncSingleActivityById(atleta, integracao, 555L);

            ArgumentCaptor<TreinoRealizado> captor = ArgumentCaptor.forClass(TreinoRealizado.class);
            verify(ingestaoTreinoRealizadoService).registrar(captor.capture(), eq("555"));
            assertThat(captor.getValue().getId()).isNull();
            assertThat(captor.getValue().getExternalId()).isEqualTo("555");
            assertThat(integracao.getUltimaSincronizacao()).isNotNull();
            verify(integracaoExternaRepository).save(integracao);
        }

        @Test
        @DisplayName("atividade já existente: find-or-new com id, registrar recebe entidade gerenciada (re-sync, D4)")
        void atividadeExistenteChamaRegistrarComEntidadeGerenciada() {
            IntegracaoExterna integracao = new IntegracaoExterna();
            StravaActivityDto activity = stravaActivity(777L);
            stubStravaCall(activity);
            TreinoRealizado existente = new TreinoRealizado();
            existente.setId(UUID.randomUUID());
            when(treinoRealizadoRepository.findByExternalIdAndAtletaId("777", atleta.getId()))
                    .thenReturn(Optional.of(existente));
            stubLapsVazias();
            when(ingestaoTreinoRealizadoService.registrar(any(), anyString()))
                    .thenAnswer(inv -> new TreinoDedupHelper.SaveResult(inv.getArgument(0), false));

            service.syncSingleActivityById(atleta, integracao, 777L);

            ArgumentCaptor<TreinoRealizado> captor = ArgumentCaptor.forClass(TreinoRealizado.class);
            verify(ingestaoTreinoRealizadoService).registrar(captor.capture(), eq("777"));
            assertThat(captor.getValue().getId()).isEqualTo(existente.getId());
            verify(ingestaoTreinoRealizadoService, never()).reprocessar(any(), any());
        }

        @Test
        @DisplayName("CA7 — webhook update sem perceived_exertion preserva RPE, feedback e sensações do atleta, e não toca o cursor do pull")
        void updatePreservaEnriquecimentoDoAtleta() {
            IntegracaoExterna integracao = new IntegracaoExterna();
            StravaActivityDto activity = stravaActivity(778L);
            stubStravaCall(activity);
            TreinoRealizado existente = new TreinoRealizado();
            existente.setId(UUID.randomUUID());
            existente.setPercepcaoEsforco(7);
            existente.setFeedbackAtleta("pernas pesadas");
            existente.setSensacoes(new java.util.HashSet<>(java.util.Set.of(br.com.menthoros.backend.enums.Sensacao.values()[0])));
            when(treinoRealizadoRepository.findByExternalIdAndAtletaId("778", atleta.getId()))
                    .thenReturn(Optional.of(existente));
            stubLapsVazias();
            when(ingestaoTreinoRealizadoService.registrar(any(), anyString()))
                    .thenAnswer(inv -> new TreinoDedupHelper.SaveResult(inv.getArgument(0), false));

            service.syncSingleActivityById(atleta, integracao, 778L);

            assertThat(existente.getPercepcaoEsforco()).isEqualTo(7);
            assertThat(existente.getFeedbackAtleta()).isEqualTo("pernas pesadas");
            assertThat(existente.getSensacoes()).hasSize(1);
            verify(integracaoExternaRepository, never()).atualizarPullCursor(any(), any(), any());
        }

        @Test
        @DisplayName("treino novo recebe o RPE que o Strava trouxer")
        void novoRecebeRpeDoStrava() {
            IntegracaoExterna integracao = new IntegracaoExterna();
            StravaActivityDto base = stravaActivity(779L);
            StravaActivityDto comRpe = new StravaActivityDto(
                    base.id(), base.name(), base.sportType(), base.startDateLocal(), base.distance(),
                    base.movingTime(), base.elapsedTime(), base.totalElevationGain(), base.averageSpeed(),
                    base.averageHeartrate(), base.maxHeartrate(), base.hasHeartrate(), base.sufferScore(),
                    6.0, base.description(), base.manual(), base.workoutType(), base.averageCadence(),
                    base.deviceName(), base.gear(), base.splitsMetric());
            stubStravaCall(comRpe);
            when(treinoRealizadoRepository.findByExternalIdAndAtletaId("779", atleta.getId()))
                    .thenReturn(Optional.empty());
            stubLapsVazias();
            when(ingestaoTreinoRealizadoService.registrar(any(), anyString()))
                    .thenAnswer(inv -> new TreinoDedupHelper.SaveResult(inv.getArgument(0), true));

            service.syncSingleActivityById(atleta, integracao, 779L);

            ArgumentCaptor<TreinoRealizado> captor = ArgumentCaptor.forClass(TreinoRealizado.class);
            verify(ingestaoTreinoRealizadoService).registrar(captor.capture(), eq("779"));
            assertThat(captor.getValue().getPercepcaoEsforco()).isEqualTo(6);
        }

        @Test
        @DisplayName("Strava reporta data diferente da já sincronizada (usuário editou no Strava): chama reprocessar com a data antiga [achado Codex]")
        void dataMudouNoStravaChamaReprocessarComDataAntiga() {
            IntegracaoExterna integracao = new IntegracaoExterna();
            StravaActivityDto activity = stravaActivity(888L);
            stubStravaCall(activity);
            TreinoRealizado existente = new TreinoRealizado();
            existente.setId(UUID.randomUUID());
            LocalDate dataAntiga = LocalDate.of(2020, 1, 1);
            existente.setDataTreino(dataAntiga);
            when(treinoRealizadoRepository.findByExternalIdAndAtletaId("888", atleta.getId()))
                    .thenReturn(Optional.of(existente));
            stubLapsVazias();
            when(ingestaoTreinoRealizadoService.registrar(any(), anyString()))
                    .thenAnswer(inv -> new TreinoDedupHelper.SaveResult(inv.getArgument(0), false));

            service.syncSingleActivityById(atleta, integracao, 888L);

            assertThat(existente.getDataTreino()).isNotEqualTo(dataAntiga);
            verify(ingestaoTreinoRealizadoService).reprocessar(existente.getId(), dataAntiga);
        }

        @Test
        @DisplayName("Strava não retorna a atividade (404/removida): não chama registrar, não persiste nada")
        void atividadeInexistenteNaoChamaRegistrar() {
            IntegracaoExterna integracao = new IntegracaoExterna();
            stubStravaCall(null);

            service.syncSingleActivityById(atleta, integracao, 999L);

            verifyNoInteractions(ingestaoTreinoRealizadoService, integracaoExternaRepository);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubStravaCall(StravaActivityDto response) {
        WebClient.RequestHeadersUriSpec rawUri = uriSpec;
        WebClient.RequestHeadersSpec rawHeaders = headersSpec;
        doReturn(rawUri).when(stravaWebClient).get();
        doReturn(rawHeaders).when(rawUri).uri(any(java.util.function.Function.class));
        doReturn(rawHeaders).when(rawHeaders).header(anyString(), anyString());
        doReturn(responseSpec).when(rawHeaders).retrieve();
        doReturn(Mono.justOrEmpty(response)).when(responseSpec).bodyToMono(StravaActivityDto.class);
    }

    private void stubLapsVazias() {
        // a busca de laps traduz 429 em StravaRateLimitException via onStatus (fix-sync-cursor-data-loss D3.6)
        doReturn(responseSpec).when(responseSpec).onStatus(any(), any());
        doReturn(Mono.just(org.springframework.http.ResponseEntity.ok(List.of())))
                .when(responseSpec).toEntityList(br.com.menthoros.backend.dto.strava.StravaSplitDto.class);
    }

    private StravaActivityDto stravaActivity(Long id) {
        return new StravaActivityDto(
                id, "Corrida", "Run", "2026-07-01T07:00:00Z",
                10000d, 3600, 3700, 50d, 2.77d, 155d, 180d,
                true, 65, null, "Treino",
                false, 0, 87d, "Garmin", null, List.of());
    }
}
