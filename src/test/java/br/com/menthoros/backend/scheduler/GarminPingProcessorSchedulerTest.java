package br.com.menthoros.backend.scheduler;

import br.com.menthoros.backend.dto.fit.FitImportResultado;
import br.com.menthoros.backend.dto.fit.FitSessionData;
import br.com.menthoros.backend.dto.output.TreinoRealizadoOutputDto;
import br.com.menthoros.backend.entity.GarminPing;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.GarminPingStatus;
import br.com.menthoros.backend.exception.FitParseException;
import br.com.menthoros.backend.exception.GarminApiException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.GarminPingRepository;
import br.com.menthoros.backend.repository.IntegracaoExternaRepository;
import br.com.menthoros.backend.services.FitParseService;
import br.com.menthoros.backend.services.GarminClient;
import br.com.menthoros.backend.services.helper.FitTreinoPersister;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatusCode;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GarminPingProcessorSchedulerTest {

    private static final Instant AGORA = Instant.parse("2026-11-05T12:00:00Z");
    private static final String CALLBACK = "https://apis.garmin.com/wellness-api/rest/activityFile?id=1&token=t";

    @Mock private GarminPingRepository pingRepository;
    @Mock private IntegracaoExternaRepository integracaoRepository;
    @Mock private GarminClient garminClient;
    @Mock private FitParseService fitParseService;
    @Mock private FitTreinoPersister fitTreinoPersister;
    @Mock private PlatformTransactionManager transactionManager;

    private GarminPingProcessorScheduler scheduler;
    private final FitSessionData dados = mock(FitSessionData.class);

    @BeforeEach
    void setUp() {
        scheduler = new GarminPingProcessorScheduler(pingRepository, integracaoRepository, garminClient,
                fitParseService, fitTreinoPersister, transactionManager, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("processar")
    class Processar {

        @Test
        @DisplayName("ping pendente vira PROCESSED com o treino, deviceName e ultimaSincronizacao gravados (CA3)")
        void sucesso() {
            GarminPing ping = ping(UUID.randomUUID());
            IntegracaoExterna conexao = filaCom(ping);
            UUID treinoId = baixaEParseia(ping, true);

            scheduler.processar();

            assertThat(ping.getStatus()).isEqualTo(GarminPingStatus.PROCESSED);
            assertThat(ping.getCompletedWorkoutId()).isEqualTo(treinoId);
            assertThat(ping.getProcessedAt()).isEqualTo(AGORA);
            assertThat(ping.getSyncingSince()).isNull();
            assertThat(ping.getReason()).isNull();
            verify(fitTreinoPersister).persistir(ping.getAthleteId(), dados, "Forerunner 970");
            verify(integracaoRepository).atualizarUltimaSincronizacao(conexao.getId(), ping.getTenantId(), AGORA);
        }

        @Test
        @DisplayName("FIT já subido pelo atleta: PROCESSED apontando para o treino existente (CA4)")
        void duplicata() {
            GarminPing ping = ping(UUID.randomUUID());
            filaCom(ping);
            UUID existente = baixaEParseia(ping, false);

            scheduler.processar();

            assertThat(ping.getStatus()).isEqualTo(GarminPingStatus.PROCESSED);
            assertThat(ping.getCompletedWorkoutId()).isEqualTo(existente);
        }

        @Test
        @DisplayName("lote pede 20 pendentes dentro da janela de 24 h")
        void lote() {
            when(pingRepository.findStuckSyncing(any())).thenReturn(List.of());
            when(pingRepository.findExpired(any())).thenReturn(List.of());
            when(pingRepository.findPendingOldestFirst(any(), any(), any())).thenReturn(List.of());

            scheduler.processar();

            verify(pingRepository).findPendingOldestFirst(eq(AGORA), eq(AGORA.minus(Duration.ofHours(24))),
                    eq(Pageable.ofSize(20)));
            verify(pingRepository).findStuckSyncing(AGORA.minus(Duration.ofMinutes(10)));
            verify(pingRepository).findExpired(AGORA.minus(Duration.ofHours(24)));
        }

        @Test
        @DisplayName("conexão revogada entre a recepção e o job: DISCARDED sem download (CA6)")
        void conexaoRevogada() {
            GarminPing ping = ping(UUID.randomUUID());
            filaSemConexao(ping);

            scheduler.processar();

            assertThat(ping.getStatus()).isEqualTo(GarminPingStatus.DISCARDED);
            assertThat(ping.getReason()).contains("inativa");
            verifyNoInteractions(garminClient, fitParseService, fitTreinoPersister);
        }

        @Test
        @DisplayName("callbackURL inválido: ERROR sem parse nem persistência (CA6b)")
        void urlInvalida() {
            GarminPing ping = ping(UUID.randomUUID());
            filaCom(ping);
            when(garminClient.baixarArquivo(CALLBACK)).thenThrow(new IllegalArgumentException("callbackURL inválido"));

            scheduler.processar();

            assertThat(ping.getStatus()).isEqualTo(GarminPingStatus.ERROR);
            assertThat(ping.getReason()).isEqualTo("callbackURL inválido");
            verifyNoInteractions(fitParseService, fitTreinoPersister);
        }

        @ParameterizedTest
        @CsvSource({"410,consumido", "404,consumido", "401,permissão", "403,permissão", "412,permissão", "400,recusado"})
        @DisplayName("4xx sem retry: ERROR com motivo (CA5, CA6c)")
        void erroSemRetry(int status, String motivo) {
            GarminPing ping = ping(UUID.randomUUID());
            filaCom(ping);
            when(garminClient.baixarArquivo(CALLBACK))
                    .thenThrow(new GarminApiException(HttpStatusCode.valueOf(status), "x"));

            scheduler.processar();

            assertThat(ping.getStatus()).isEqualTo(GarminPingStatus.ERROR);
            assertThat(ping.getReason()).contains(motivo).contains(String.valueOf(status));
            assertThat(ping.getAttempts()).isZero();
            verifyNoInteractions(fitTreinoPersister);
        }

        @ParameterizedTest
        @ValueSource(ints = {429, 500, 502, 503})
        @DisplayName("429 e 5xx voltam a PENDING com tentativas+1 e backoff (CA5, CA6c)")
        void retry(int status) {
            GarminPing ping = ping(UUID.randomUUID());
            filaCom(ping);
            when(garminClient.baixarArquivo(CALLBACK))
                    .thenThrow(new GarminApiException(HttpStatusCode.valueOf(status), "x"));

            scheduler.processar();

            assertThat(ping.getStatus()).isEqualTo(GarminPingStatus.PENDING);
            assertThat(ping.getAttempts()).isEqualTo(1);
            assertThat(ping.getNextAttemptAt()).isEqualTo(AGORA.plus(Duration.ofMinutes(2)));
            assertThat(ping.getSyncingSince()).isNull();
            verifyNoInteractions(fitTreinoPersister);
        }

        @Test
        @DisplayName("timeout (status nulo) segue o backoff")
        void timeout() {
            GarminPing ping = ping(UUID.randomUUID());
            filaCom(ping);
            when(garminClient.baixarArquivo(CALLBACK))
                    .thenThrow(new GarminApiException("Falha de transporte", new RuntimeException()));

            scheduler.processar();

            assertThat(ping.getStatus()).isEqualTo(GarminPingStatus.PENDING);
            assertThat(ping.getAttempts()).isEqualTo(1);
        }

        @Test
        @DisplayName("6 falhas 5xx seguidas dentro da janela não viram ERROR; backoff para em 60 min")
        void seisFalhasSemErro() {
            GarminPing ping = ping(UUID.randomUUID());
            filaCom(ping);
            when(garminClient.baixarArquivo(CALLBACK))
                    .thenThrow(new GarminApiException(HttpStatusCode.valueOf(503), "x"));

            List<Long> esperas = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                scheduler.processar();
                esperas.add(Duration.between(AGORA, ping.getNextAttemptAt()).toMinutes());
                assertThat(ping.getStatus()).isEqualTo(GarminPingStatus.PENDING);
            }

            assertThat(ping.getAttempts()).isEqualTo(6);
            assertThat(esperas).containsExactly(2L, 4L, 8L, 16L, 32L, 60L);
        }

        @Test
        @DisplayName("FIT inválido: ERROR sem retry")
        void fitInvalido() {
            GarminPing ping = ping(UUID.randomUUID());
            filaCom(ping);
            when(garminClient.baixarArquivo(CALLBACK)).thenReturn(new byte[]{1});
            when(fitParseService.parse(any())).thenThrow(new FitParseException("ruim"));

            scheduler.processar();

            assertThat(ping.getStatus()).isEqualTo(GarminPingStatus.ERROR);
            assertThat(ping.getReason()).contains("FIT");
            verifyNoInteractions(fitTreinoPersister);
        }

        @Test
        @DisplayName("pendente com mais de 24 h vira ERROR expirado sem download (CA7)")
        void expira() {
            GarminPing velho = ping(UUID.randomUUID());
            when(pingRepository.findStuckSyncing(any())).thenReturn(List.of());
            when(pingRepository.findExpired(any())).thenReturn(List.of(velho));
            when(pingRepository.findPendingOldestFirst(any(), any(), any())).thenReturn(List.of());

            scheduler.processar();

            assertThat(velho.getStatus()).isEqualTo(GarminPingStatus.ERROR);
            assertThat(velho.getReason()).contains("Expirado");
            verify(pingRepository).save(velho);
            verifyNoInteractions(garminClient);
        }

        @Test
        @DisplayName("SYNCING preso há mais de 10 min volta a PENDING com tentativas+1 (CA7b)")
        void reclaim() {
            GarminPing preso = ping(UUID.randomUUID());
            preso.setStatus(GarminPingStatus.SYNCING);
            preso.setSyncingSince(AGORA.minus(Duration.ofMinutes(11)));
            when(pingRepository.findStuckSyncing(any())).thenReturn(List.of(preso));
            when(pingRepository.findExpired(any())).thenReturn(List.of());
            when(pingRepository.findPendingOldestFirst(any(), any(), any())).thenReturn(List.of());

            scheduler.processar();

            assertThat(preso.getStatus()).isEqualTo(GarminPingStatus.PENDING);
            assertThat(preso.getAttempts()).isEqualTo(1);
            assertThat(preso.getSyncingSince()).isNull();
            assertThat(preso.getNextAttemptAt()).isEqualTo(AGORA);
        }

        @Test
        @DisplayName("lote com dois tenants: cada treino persiste no tenant do seu ping e o contexto é limpo")
        void doisTenants() {
            GarminPing a = ping(UUID.randomUUID());
            GarminPing b = ping(UUID.randomUUID());
            b.setSummaryId("s2");
            when(pingRepository.findStuckSyncing(any())).thenReturn(List.of());
            when(pingRepository.findExpired(any())).thenReturn(List.of());
            when(pingRepository.findPendingOldestFirst(any(), any(), any())).thenReturn(List.of(a, b));
            when(pingRepository.save(any(GarminPing.class))).thenAnswer(inv -> inv.getArgument(0));
            when(integracaoRepository.findActiveByAtletaIdAndPlataformaAndTenantId(any(), eq(FonteDados.GARMIN), any()))
                    .thenAnswer(inv -> Optional.of(conexao(inv.getArgument(2))));
            when(garminClient.baixarArquivo(CALLBACK)).thenReturn(new byte[]{1});
            when(fitParseService.parse(any())).thenReturn(dados);
            List<UUID> tenantsNoPersist = new ArrayList<>();
            when(fitTreinoPersister.persistir(any(), eq(dados), anyString())).thenAnswer(inv -> {
                tenantsNoPersist.add(TenantContext.getRequiredTenantId());
                return resultado(UUID.randomUUID(), true);
            });

            scheduler.processar();

            assertThat(tenantsNoPersist).containsExactly(a.getTenantId(), b.getTenantId());
            assertThat(TenantContext.hasTenant()).isFalse();
            assertThat(a.getStatus()).isEqualTo(GarminPingStatus.PROCESSED);
            assertThat(b.getStatus()).isEqualTo(GarminPingStatus.PROCESSED);
        }

        @Test
        @DisplayName("falha inesperada num ping não aborta o lote e não vaza contexto")
        void falhaNaoAbortaLote() {
            GarminPing a = ping(UUID.randomUUID());
            GarminPing b = ping(UUID.randomUUID());
            when(pingRepository.findStuckSyncing(any())).thenReturn(List.of());
            when(pingRepository.findExpired(any())).thenReturn(List.of());
            when(pingRepository.findPendingOldestFirst(any(), any(), any())).thenReturn(List.of(a, b));
            when(pingRepository.save(any(GarminPing.class))).thenAnswer(inv -> inv.getArgument(0));
            when(integracaoRepository.findActiveByAtletaIdAndPlataformaAndTenantId(any(), eq(FonteDados.GARMIN), any()))
                    .thenAnswer(inv -> Optional.of(conexao(inv.getArgument(2))));
            when(garminClient.baixarArquivo(CALLBACK)).thenReturn(new byte[]{1});
            when(fitParseService.parse(any())).thenReturn(dados);
            FitImportResultado ok = resultado(UUID.randomUUID(), true);
            when(fitTreinoPersister.persistir(any(), eq(dados), anyString()))
                    .thenThrow(new IllegalStateException("banco caiu"))
                    .thenReturn(ok);

            scheduler.processar();

            assertThat(b.getStatus()).isEqualTo(GarminPingStatus.PROCESSED);
            assertThat(TenantContext.hasTenant()).isFalse();
            verify(integracaoRepository, never()).atualizarUltimaSincronizacao(any(), eq(a.getTenantId()), any());
        }
    }

    private IntegracaoExterna filaCom(GarminPing ping) {
        IntegracaoExterna conexao = conexao(ping.getTenantId());
        when(pingRepository.findStuckSyncing(any())).thenReturn(List.of());
        when(pingRepository.findExpired(any())).thenReturn(List.of());
        when(pingRepository.findPendingOldestFirst(any(), any(), any())).thenReturn(List.of(ping));
        when(pingRepository.save(any(GarminPing.class))).thenAnswer(inv -> inv.getArgument(0));
        when(integracaoRepository.findActiveByAtletaIdAndPlataformaAndTenantId(
                ping.getAthleteId(), FonteDados.GARMIN, ping.getTenantId())).thenReturn(Optional.of(conexao));
        return conexao;
    }

    private void filaSemConexao(GarminPing ping) {
        when(pingRepository.findStuckSyncing(any())).thenReturn(List.of());
        when(pingRepository.findExpired(any())).thenReturn(List.of());
        when(pingRepository.findPendingOldestFirst(any(), any(), any())).thenReturn(List.of(ping));
        when(pingRepository.save(any(GarminPing.class))).thenAnswer(inv -> inv.getArgument(0));
        when(integracaoRepository.findActiveByAtletaIdAndPlataformaAndTenantId(
                ping.getAthleteId(), FonteDados.GARMIN, ping.getTenantId())).thenReturn(Optional.empty());
    }

    private UUID baixaEParseia(GarminPing ping, boolean novo) {
        UUID treinoId = UUID.randomUUID();
        when(garminClient.baixarArquivo(CALLBACK)).thenReturn(new byte[]{1, 2, 3});
        when(fitParseService.parse(any())).thenReturn(dados);
        FitImportResultado resultado = resultado(treinoId, novo);
        when(fitTreinoPersister.persistir(ping.getAthleteId(), dados, "Forerunner 970")).thenReturn(resultado);
        return treinoId;
    }

    private static FitImportResultado resultado(UUID treinoId, boolean novo) {
        TreinoRealizadoOutputDto dto = mock(TreinoRealizadoOutputDto.class);
        when(dto.id()).thenReturn(treinoId);
        return new FitImportResultado(dto, novo);
    }

    private static IntegracaoExterna conexao(UUID tenantId) {
        IntegracaoExterna conexao = new IntegracaoExterna();
        conexao.setId(UUID.randomUUID());
        conexao.setTenantId(tenantId);
        conexao.setPlataforma(FonteDados.GARMIN);
        conexao.setAtivo(true);
        return conexao;
    }

    private static GarminPing ping(UUID tenantId) {
        GarminPing ping = new GarminPing();
        ping.setId(UUID.randomUUID());
        ping.setTenantId(tenantId);
        ping.setAthleteId(UUID.randomUUID());
        ping.setGarminUserId("u1");
        ping.setSummaryId("s1");
        ping.setCallbackUrl(CALLBACK);
        ping.setActivityType("RUNNING");
        ping.setFileType("FIT");
        ping.setDeviceName("Forerunner 970");
        ping.setStartTime(AGORA.minus(Duration.ofHours(1)));
        ping.setStatus(GarminPingStatus.PENDING);
        ping.setCreatedAt(AGORA.minus(Duration.ofMinutes(5)));
        ping.setNextAttemptAt(AGORA.minus(Duration.ofMinutes(1)));
        return ping;
    }
}
