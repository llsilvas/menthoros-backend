package br.com.menthoros.backend.services;

import br.com.menthoros.backend.config.external.StravaProperties;
import br.com.menthoros.backend.config.external.StravaWebClientConfig;
import br.com.menthoros.backend.dto.output.StravaSyncResponseDto;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.ErroCategoriaPull;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.ResultadoPull;
import br.com.menthoros.backend.mapper.TreinoMapper;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.IntegracaoExternaRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.helper.PullResultado;
import br.com.menthoros.backend.services.helper.SyncDescarteWriter;
import br.com.menthoros.backend.services.helper.TreinoDedupHelper;
import br.com.menthoros.backend.services.impl.StravaActivityServiceImpl;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pull do Strava por fatias de tempo (fix-sync-cursor-data-loss, design D3). WebClient REAL contra
 * WireMock: paginação, fatias, fronteira exclusiva e 429 sem header só são testáveis de verdade
 * atravessando o HTTP — a cadeia de mocks do WebClient concordaria com qualquer implementação.
 *
 * <p>Transação por atividade com {@link TransactionOperations#withoutTransaction()}: o que está sob
 * teste é a ordem das chamadas e o que conta como progresso; o rollback é do Spring.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StravaActivityPullTest {

    private static final String TOKEN = "token-valido";
    private static final long FATIA_S = Duration.ofDays(14).toSeconds();

    @Mock private AtletaRepository atletaRepository;
    @Mock private TreinoRealizadoRepository treinoRealizadoRepository;
    @Mock private IntegracaoExternaRepository integracaoExternaRepository;
    @Mock private StravaOAuthService stravaOAuthService;
    @Mock private TreinoMapper treinoMapper;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private IngestaoTreinoRealizadoService ingestaoTreinoRealizadoService;
    @Mock private SyncDescarteWriter descarteWriter;

    private WireMockServer wireMock;
    private StravaActivityServiceImpl service;
    private UUID integracaoId;
    private UUID tenantId;
    private UUID atletaId;
    private Atleta atleta;
    private IntegracaoExterna integracao;
    private Instant inicioDoTeste;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
        StravaProperties props = new StravaProperties();
        props.setApiBaseUrl(wireMock.baseUrl());

        service = new StravaActivityServiceImpl(atletaRepository, treinoRealizadoRepository,
                integracaoExternaRepository, stravaOAuthService, treinoMapper, eventPublisher,
                new StravaWebClientConfig(props).stravaWebClient(), ingestaoTreinoRealizadoService,
                TransactionOperations.withoutTransaction(), descarteWriter, props);

        integracaoId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        atletaId = UUID.randomUUID();
        Assessoria assessoria = new Assessoria();
        assessoria.setId(tenantId);
        atleta = new Atleta();
        atleta.setId(atletaId);
        atleta.setAssessoria(assessoria);
        inicioDoTeste = Instant.now();
        TenantContext.setTenantId(tenantId);

        when(stravaOAuthService.getValidToken(atletaId)).thenReturn(TOKEN);
        when(atletaRepository.findByIdAndTenantId(atletaId, tenantId)).thenReturn(Optional.of(atleta));
        when(treinoRealizadoRepository.findByExternalIdAndAtletaId(anyString(), eq(atletaId))).thenReturn(Optional.empty());
        when(ingestaoTreinoRealizadoService.registrar(any(), anyString()))
                .thenAnswer(inv -> new TreinoDedupHelper.SaveResult(inv.getArgument(0), true));
        when(descarteWriter.descartadas(any(), any(), eq(FonteDados.STRAVA))).thenReturn(Set.of());
        when(integracaoExternaRepository.atualizarPullCursor(any(), any(), any())).thenReturn(1);
        when(integracaoExternaRepository.atualizarStatusSync(any(), any(), any(), any(), any())).thenReturn(1);

        // padrão: toda página vazia, laps vazias — cada teste sobrescreve o que precisa
        wireMock.stubFor(get(urlPathEqualTo("/athlete/activities")).atPriority(10).willReturn(okJson("[]")));
        wireMock.stubFor(get(urlPathMatching("/activities/.*/laps")).willReturn(okJson("[]")));
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
        TenantContext.clear();
    }

    // ----------------------------------------------------------------- fixtures

    private void integracaoComCursor(Instant pullCursor) {
        integracao = new IntegracaoExterna();
        integracao.setId(integracaoId);
        integracao.setTenantId(tenantId);
        integracao.setPlataforma(FonteDados.STRAVA);
        integracao.setAtivo(true);
        integracao.setAtleta(atleta);
        integracao.setSyncActivityCount(4);
        integracao.setUltimaSincronizacao(Instant.now().minus(Duration.ofDays(1)));
        ReflectionTestUtils.setField(integracao, "pullCursor", pullCursor);
        when(integracaoExternaRepository.findActiveByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.STRAVA, tenantId))
                .thenReturn(Optional.of(integracao));
        when(integracaoExternaRepository.findByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.STRAVA, tenantId))
                .thenReturn(Optional.of(integracao));
    }

    private static String atividade(long id, String sportType, String startDateLocal) {
        return """
                {"id":%d,"name":"Treino %d","sport_type":"%s","start_date":"2026-09-10T06:00:00Z",
                 "start_date_local":"%s","distance":5000.0,"moving_time":1800,"elapsed_time":1850}
                """.formatted(id, id, sportType, startDateLocal);
    }

    private static String corrida(long id) {
        return atividade(id, "Run", "2026-09-10T09:00:00Z");
    }

    private static String pagina(String... atividades) {
        return "[" + String.join(",", atividades) + "]";
    }

    /**
     * Página com 30 itens (as corridas + bikes para completar): só página cheia faz a varredura pedir a
     * próxima — página curta é a última (task 0.1).
     */
    private static String paginaCheia(long idBase, String... corridas) {
        List<String> itens = new java.util.ArrayList<>(List.of(corridas));
        for (int i = itens.size(); i < 30; i++) {
            itens.add(atividade(idBase + i, "Ride", "2026-09-10T08:00:00Z"));
        }
        return "[" + String.join(",", itens) + "]";
    }

    private void paginaDaFatia(long after, int page, String corpo) {
        wireMock.stubFor(get(urlPathEqualTo("/athlete/activities")).atPriority(1)
                .withQueryParam("after", equalTo(String.valueOf(after)))
                .withQueryParam("page", equalTo(String.valueOf(page)))
                .willReturn(okJson(corpo)));
    }

    private void paginaDeQualquerFatia(int page, String corpo) {
        wireMock.stubFor(get(urlPathEqualTo("/athlete/activities")).atPriority(5)
                .withQueryParam("page", equalTo(String.valueOf(page)))
                .willReturn(okJson(corpo)));
    }

    private void statusNaPagina(int page, int status) {
        wireMock.stubFor(get(urlPathEqualTo("/athlete/activities")).atPriority(4)
                .withQueryParam("page", equalTo(String.valueOf(page)))
                .willReturn(aResponse().withStatus(status)));
    }

    private List<Instant> cursoresGravados() {
        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(integracaoExternaRepository, org.mockito.Mockito.atLeast(0))
                .atualizarPullCursor(eq(integracaoId), eq(tenantId), captor.capture());
        return captor.getAllValues();
    }

    private List<String> importados() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(ingestaoTreinoRealizadoService, org.mockito.Mockito.atLeast(0)).registrar(any(), captor.capture());
        return captor.getAllValues();
    }

    /** Cursor em segundos inteiros: o parâmetro {@code after} da API é epoch em segundos. */
    private static Instant cursorDiasAtras(int dias) {
        return Instant.now().minus(Duration.ofDays(dias)).truncatedTo(ChronoUnit.SECONDS);
    }

    private static long inicioDaJanela(Instant cursor) {
        return cursor.minus(Duration.ofDays(7)).getEpochSecond();
    }

    // ----------------------------------------------------------------- pullAgendado

    @Nested
    @DisplayName("pullAgendado — varredura por fatias")
    class PullAgendado {

        @Test
        @DisplayName("CA3 — página de outras modalidades não encerra a fatia: a paginação segue pela página original")
        void paginaSoDeOutrasModalidades() {
            Instant cursor = cursorDiasAtras(1);
            integracaoComCursor(cursor);
            String bikes = IntStream.rangeClosed(1, 30)
                    .mapToObj(i -> atividade(1000 + i, "Ride", "2026-09-10T08:00:00Z"))
                    .collect(Collectors.joining(","));
            paginaDaFatia(inicioDaJanela(cursor), 1, "[" + bikes + "]");
            paginaDaFatia(inicioDaJanela(cursor), 2, pagina(corrida(1), corrida(2), corrida(3)));

            PullResultado resultado = service.pullAgendado(atletaId);

            assertThat(importados()).containsExactly("1", "2", "3");
            assertThat(resultado).isEqualTo(new PullResultado(ResultadoPull.COMPLETO, null, 3, 0));
            assertThat(cursoresGravados()).singleElement()
                    .satisfies(c -> assertThat(c).isBetween(inicioDoTeste, Instant.now()));
        }

        @Test
        @DisplayName("CA6 — o cursor vem do fim da fatia, nunca do start_date_local (hora local rotulada como UTC)")
        void cursorNaoDependeDoStartDateLocal() {
            Instant cursor = cursorDiasAtras(1);
            integracaoComCursor(cursor);
            String localNoFuturo = Instant.now().plus(Duration.ofHours(3)).truncatedTo(ChronoUnit.SECONDS).toString();
            paginaDaFatia(inicioDaJanela(cursor), 1, pagina(atividade(7, "Run", localNoFuturo)));

            service.pullAgendado(atletaId);

            assertThat(cursoresGravados()).singleElement()
                    .satisfies(c -> assertThat(c).isBeforeOrEqualTo(Instant.now()));
        }

        @Test
        @DisplayName("CA10 — rate limit na página 3 da segunda fatia: cursor no fim da primeira; inserções preservadas")
        void fatiaRetomavel() {
            Instant cursor = cursorDiasAtras(20);
            integracaoComCursor(cursor);
            long inicio = inicioDaJanela(cursor);
            // 1ª fatia completa e vazia (página 1 vazia); 2ª fatia: páginas 1 e 2 cheias, com uma corrida
            // cada, e a 3 com 429
            paginaDaFatia(inicio, 1, "[]");
            paginaDeQualquerFatia(1, paginaCheia(2000, corrida(11)));
            paginaDeQualquerFatia(2, paginaCheia(3000, corrida(12)));
            statusNaPagina(3, 429);

            PullResultado resultado = service.pullAgendado(atletaId);

            assertThat(cursoresGravados()).containsExactly(Instant.ofEpochSecond(inicio + FATIA_S));
            assertThat(importados()).containsExactly("11", "12");
            assertThat(resultado).isEqualTo(new PullResultado(ResultadoPull.PARCIAL, ErroCategoriaPull.RATE_LIMIT, 2, 0));
        }

        @Test
        @DisplayName("fatias consecutivas se sobrepõem em 60 s (after e before são exclusivos na API)")
        void sobreposicaoDaFronteira() {
            Instant cursor = cursorDiasAtras(20);
            integracaoComCursor(cursor);
            long inicio = inicioDaJanela(cursor);
            long fimPrimeira = inicio + FATIA_S;

            service.pullAgendado(atletaId);

            wireMock.verify(getRequestedFor(urlPathEqualTo("/athlete/activities"))
                    .withQueryParam("after", equalTo(String.valueOf(inicio)))
                    .withQueryParam("before", equalTo(String.valueOf(fimPrimeira))));
            wireMock.verify(getRequestedFor(urlPathEqualTo("/athlete/activities"))
                    .withQueryParam("after", equalTo(String.valueOf(fimPrimeira - 60))));
        }

        @Test
        @DisplayName("atividade no segundo exato da fronteira aparece nas duas fatias e é importada uma vez")
        void atividadeNaFronteiraImportadaUmaVez() {
            Instant cursor = cursorDiasAtras(20);
            integracaoComCursor(cursor);
            paginaDeQualquerFatia(1, pagina(corrida(99)));
            when(treinoRealizadoRepository.findByExternalIdAndAtletaId("99", atletaId))
                    .thenReturn(Optional.empty(), Optional.of(new TreinoRealizado()));

            service.pullAgendado(atletaId);

            assertThat(importados()).containsExactly("99");
        }

        @Test
        @DisplayName("CA4 — conexão nova (cursor nulo) com 429 na primeira página: horizonte gravado antes, FALHA/RATE_LIMIT")
        void horizonteInicialComRateLimit() {
            integracaoComCursor(null);
            statusNaPagina(1, 429);

            PullResultado resultado = service.pullAgendado(atletaId);

            assertThat(cursoresGravados()).singleElement().satisfies(h -> assertThat(h)
                    .isBetween(inicioDoTeste.minus(Duration.ofDays(90)).minusSeconds(5), Instant.now().minus(Duration.ofDays(90))));
            assertThat(resultado).isEqualTo(new PullResultado(ResultadoPull.FALHA, ErroCategoriaPull.RATE_LIMIT, 0, 0));
        }

        @Test
        @DisplayName("401 na listagem → FALHA/CREDENCIAL, cursor intocado")
        void credencialRevogada() {
            integracaoComCursor(cursorDiasAtras(1));
            statusNaPagina(1, 401);

            PullResultado resultado = service.pullAgendado(atletaId);

            assertThat(cursoresGravados()).isEmpty();
            assertThat(resultado).isEqualTo(new PullResultado(ResultadoPull.FALHA, ErroCategoriaPull.CREDENCIAL, 0, 0));
        }

        @Test
        @DisplayName("503 na listagem → TRANSITORIO")
        void indisponivel() {
            integracaoComCursor(cursorDiasAtras(1));
            statusNaPagina(1, 503);

            assertThat(service.pullAgendado(atletaId).erro()).isEqualTo(ErroCategoriaPull.TRANSITORIO);
        }

        @Test
        @DisplayName("CA7 — já importada é pulada: sem laps, sem registrar, não conta como inserção")
        void jaImportadaEPulada() {
            Instant cursor = cursorDiasAtras(1);
            integracaoComCursor(cursor);
            paginaDaFatia(inicioDaJanela(cursor), 1, pagina(corrida(5)));
            when(treinoRealizadoRepository.findByExternalIdAndAtletaId("5", atletaId))
                    .thenReturn(Optional.of(new TreinoRealizado()));

            PullResultado resultado = service.pullAgendado(atletaId);

            verify(ingestaoTreinoRealizadoService, never()).registrar(any(), anyString());
            wireMock.verify(0, getRequestedFor(urlPathMatching("/activities/.*/laps")));
            assertThat(resultado).isEqualTo(new PullResultado(ResultadoPull.COMPLETO, null, 0, 0));
        }

        @Test
        @DisplayName("corrida concorrente com o webhook (registrar devolve inserted = false) não conta")
        void corridaConcorrenteNaoConta() {
            Instant cursor = cursorDiasAtras(1);
            integracaoComCursor(cursor);
            paginaDaFatia(inicioDaJanela(cursor), 1, pagina(corrida(6)));
            when(ingestaoTreinoRealizadoService.registrar(any(), eq("6")))
                    .thenAnswer(inv -> new TreinoDedupHelper.SaveResult(inv.getArgument(0), false));

            assertThat(service.pullAgendado(atletaId).insercoes()).isZero();
        }

        @Test
        @DisplayName("descartada não é buscada de novo")
        void descartadaEPulada() {
            Instant cursor = cursorDiasAtras(1);
            integracaoComCursor(cursor);
            paginaDaFatia(inicioDaJanela(cursor), 1, pagina(corrida(8), corrida(9)));
            when(descarteWriter.descartadas(tenantId, atletaId, FonteDados.STRAVA)).thenReturn(Set.of("8"));

            service.pullAgendado(atletaId);

            assertThat(importados()).containsExactly("9");
        }

        @Test
        @DisplayName("exceção inesperada numa atividade: não desfaz as anteriores, conta tentativa e para a fatia")
        void inesperadaPreservaAnteriores() {
            Instant cursor = cursorDiasAtras(1);
            integracaoComCursor(cursor);
            paginaDaFatia(inicioDaJanela(cursor), 1, pagina(corrida(1), corrida(2), corrida(3)));
            when(ingestaoTreinoRealizadoService.registrar(any(), eq("2"))).thenThrow(new IllegalStateException("erro"));

            PullResultado resultado = service.pullAgendado(atletaId);

            verify(descarteWriter).registrarFalha(tenantId, atletaId, FonteDados.STRAVA, "2");
            assertThat(importados()).containsExactly("1", "2");
            assertThat(cursoresGravados()).isEmpty();
            assertThat(resultado).isEqualTo(new PullResultado(ResultadoPull.PARCIAL, ErroCategoriaPull.INESPERADO, 1, 0));
        }

        @Test
        @DisplayName("CA12 — na 3ª tentativa a atividade é descartada e a fatia segue")
        void terceiraTentativaDescarta() {
            Instant cursor = cursorDiasAtras(1);
            integracaoComCursor(cursor);
            paginaDaFatia(inicioDaJanela(cursor), 1, pagina(corrida(1), corrida(2), corrida(3)));
            when(ingestaoTreinoRealizadoService.registrar(any(), eq("2"))).thenThrow(new IllegalStateException("erro"));
            when(descarteWriter.registrarFalha(tenantId, atletaId, FonteDados.STRAVA, "2")).thenReturn(true);

            PullResultado resultado = service.pullAgendado(atletaId);

            assertThat(importados()).containsExactly("1", "2", "3");
            assertThat(cursoresGravados()).hasSize(1);
            assertThat(resultado).isEqualTo(new PullResultado(ResultadoPull.PARCIAL, ErroCategoriaPull.INESPERADO, 2, 1));
            // QA: o coach precisa saber que houve atividade não importada
            verify(integracaoExternaRepository).atualizarStatusSync(eq(integracaoId), eq(tenantId), any(), any(),
                    org.mockito.ArgumentMatchers.contains("1 atividade(s) não importada(s)"));
        }

        @Test
        @DisplayName("QA — descartada numa fatia não é tentada de novo na sobreposição com a seguinte")
        void descarteValeParaAFatiaSeguinte() {
            integracaoComCursor(cursorDiasAtras(20));
            paginaDeQualquerFatia(1, pagina(corrida(2)));
            when(ingestaoTreinoRealizadoService.registrar(any(), eq("2"))).thenThrow(new IllegalStateException("erro"));
            when(descarteWriter.registrarFalha(tenantId, atletaId, FonteDados.STRAVA, "2")).thenReturn(true);

            PullResultado resultado = service.pullAgendado(atletaId);

            verify(descarteWriter, times(1)).registrarFalha(tenantId, atletaId, FonteDados.STRAVA, "2");
            assertThat(resultado.ignoradas()).isEqualTo(1);
        }

        @Test
        @DisplayName("QA — falha de banco (deadlock) é transitória: não conta tentativa, para a fatia sem avançar")
        void falhaDeBancoNaoContaTentativa() {
            Instant cursor = cursorDiasAtras(1);
            integracaoComCursor(cursor);
            paginaDaFatia(inicioDaJanela(cursor), 1, pagina(corrida(1), corrida(2)));
            when(ingestaoTreinoRealizadoService.registrar(any(), eq("2")))
                    .thenThrow(new org.springframework.dao.CannotAcquireLockException("deadlock"));

            PullResultado resultado = service.pullAgendado(atletaId);

            verify(descarteWriter, never()).registrarFalha(any(), any(), any(), any());
            assertThat(cursoresGravados()).isEmpty();
            assertThat(resultado).isEqualTo(new PullResultado(ResultadoPull.PARCIAL, ErroCategoriaPull.TRANSITORIO, 1, 0));
        }

        @Test
        @DisplayName("QA — violação de constraint é determinística: conta tentativa e, na 3ª, descarta (não trava a fatia)")
        void violacaoDeConstraintContaTentativa() {
            Instant cursor = cursorDiasAtras(1);
            integracaoComCursor(cursor);
            paginaDaFatia(inicioDaJanela(cursor), 1, pagina(corrida(1), corrida(2)));
            when(ingestaoTreinoRealizadoService.registrar(any(), eq("2")))
                    .thenThrow(new org.springframework.dao.DataIntegrityViolationException("value too long"));
            when(descarteWriter.registrarFalha(tenantId, atletaId, FonteDados.STRAVA, "2")).thenReturn(true);

            PullResultado resultado = service.pullAgendado(atletaId);

            verify(descarteWriter).registrarFalha(tenantId, atletaId, FonteDados.STRAVA, "2");
            assertThat(cursoresGravados()).hasSize(1);
            assertThat(resultado).isEqualTo(new PullResultado(ResultadoPull.PARCIAL, ErroCategoriaPull.INESPERADO, 1, 1));
        }

        @Test
        @DisplayName("QA — página curta é a última: sem requisição extra por fatia")
        void paginaCurtaEncerraAFatia() {
            Instant cursor = cursorDiasAtras(1);
            integracaoComCursor(cursor);
            paginaDaFatia(inicioDaJanela(cursor), 1, pagina(corrida(1)));

            service.pullAgendado(atletaId);

            wireMock.verify(1, getRequestedFor(urlPathEqualTo("/athlete/activities")));
        }

        @Test
        @DisplayName("QA — laps são buscados ANTES de abrir a transação da atividade (conexão não fica presa no HTTP)")
        void lapsForaDaTransacao() {
            Instant cursor = cursorDiasAtras(1);
            integracaoComCursor(cursor);
            paginaDaFatia(inicioDaJanela(cursor), 1, pagina(corrida(1)));
            List<Integer> lapsJaBuscadosAoAbrirTransacao = new java.util.ArrayList<>();
            TransactionOperations espia = new TransactionOperations() {
                @Override
                public <T> T execute(org.springframework.transaction.support.TransactionCallback<T> action) {
                    lapsJaBuscadosAoAbrirTransacao.add(
                            wireMock.countRequestsMatching(getRequestedFor(urlPathMatching("/activities/.*/laps")).build()).getCount());
                    return action.doInTransaction(null);
                }
            };
            StravaProperties props = new StravaProperties();
            props.setApiBaseUrl(wireMock.baseUrl());
            StravaActivityServiceImpl comEspia = new StravaActivityServiceImpl(atletaRepository, treinoRealizadoRepository,
                    integracaoExternaRepository, stravaOAuthService, treinoMapper, eventPublisher,
                    new StravaWebClientConfig(props).stravaWebClient(), ingestaoTreinoRealizadoService,
                    espia, descarteWriter, props);

            comEspia.pullAgendado(atletaId);

            assertThat(lapsJaBuscadosAoAbrirTransacao).containsExactly(1);
        }

        @Test
        @DisplayName("QA — cota zerada no header dos laps: a atividade é gravada, e só depois o ciclo para")
        void cotaZeradaNosLapsNaoDesfazAAtividade() {
            Instant cursor = cursorDiasAtras(1);
            integracaoComCursor(cursor);
            paginaDaFatia(inicioDaJanela(cursor), 1, pagina(corrida(1), corrida(2)));
            wireMock.stubFor(get(urlPathEqualTo("/activities/1/laps")).atPriority(1)
                    .willReturn(okJson("[]").withHeader("X-RateLimit-Remaining", "0,500")));

            PullResultado resultado = service.pullAgendado(atletaId);

            assertThat(importados()).containsExactly("1");
            assertThat(resultado).isEqualTo(new PullResultado(ResultadoPull.PARCIAL, ErroCategoriaPull.RATE_LIMIT, 1, 0));
            assertThat(cursoresGravados()).isEmpty();
        }

        @Test
        @DisplayName("status: ultimaSincronizacao = agora, contador somado, sem erro; nunca save da entidade")
        void statusPontual() {
            Instant cursor = cursorDiasAtras(1);
            integracaoComCursor(cursor);
            paginaDaFatia(inicioDaJanela(cursor), 1, pagina(corrida(1), corrida(2)));

            service.pullAgendado(atletaId);

            ArgumentCaptor<Instant> ultima = ArgumentCaptor.forClass(Instant.class);
            verify(integracaoExternaRepository).atualizarStatusSync(eq(integracaoId), eq(tenantId), ultima.capture(), eq(6), eq(null));
            assertThat(ultima.getValue()).isAfterOrEqualTo(inicioDoTeste);
            verify(integracaoExternaRepository, never()).save(any());
        }

        @Test
        @DisplayName("CA8 — 3 inserções e falha ao gravar o status → PARCIAL com 3, não FALHA/0")
        void falhaNaFinalizacao() {
            Instant cursor = cursorDiasAtras(1);
            integracaoComCursor(cursor);
            paginaDaFatia(inicioDaJanela(cursor), 1, pagina(corrida(1), corrida(2), corrida(3)));
            when(integracaoExternaRepository.atualizarStatusSync(any(), any(), any(), any(), any()))
                    .thenThrow(new IllegalStateException("conexão caiu"));

            assertThat(service.pullAgendado(atletaId))
                    .isEqualTo(new PullResultado(ResultadoPull.PARCIAL, ErroCategoriaPull.INESPERADO, 3, 0));
        }

        @Test
        @DisplayName("falha ao obter o token → FALHA/CREDENCIAL, sem chamada à API")
        void tokenInvalido() {
            integracaoComCursor(cursorDiasAtras(1));
            when(stravaOAuthService.getValidToken(atletaId))
                    .thenThrow(new IllegalStateException("Token Strava expirado. O atleta precisa reconectar a integração."));

            PullResultado resultado = service.pullAgendado(atletaId);

            assertThat(resultado).isEqualTo(new PullResultado(ResultadoPull.FALHA, ErroCategoriaPull.CREDENCIAL, 0, 0));
            wireMock.verify(0, getRequestedFor(urlPathEqualTo("/athlete/activities")));
        }
    }

    // ----------------------------------------------------------------- sync manual

    @Nested
    @DisplayName("syncActivitiesForAtleta — sync manual lê o cursor e nunca o grava")
    class SyncManual {

        @Test
        @DisplayName("CA2 — sync manual completo não grava pull_cursor e parte de pull_cursor − overlap")
        void manualNaoMoveOCursor() {
            // uma fatia só: com duas, o stub devolveria a mesma corrida nas duas e o mock de "já importada" não muda
            Instant cursor = cursorDiasAtras(1);
            integracaoComCursor(cursor);
            paginaDeQualquerFatia(1, pagina(corrida(1)));

            StravaSyncResponseDto resposta = service.syncActivitiesForAtleta(atletaId, tenantId);

            assertThat(cursoresGravados()).isEmpty();
            wireMock.verify(getRequestedFor(urlPathEqualTo("/athlete/activities"))
                    .withQueryParam("after", equalTo(String.valueOf(inicioDaJanela(cursor)))));
            assertThat(resposta.imported()).isEqualTo(1);
            verify(integracaoExternaRepository).atualizarStatusSync(eq(integracaoId), eq(tenantId), any(), eq(1), eq(null));
        }

        @Test
        @DisplayName("CA2 — sync manual com rate limit: 200 parcial, cursor intocado, lastSyncError com a frase")
        void manualComRateLimit() {
            integracaoComCursor(cursorDiasAtras(1));
            paginaDeQualquerFatia(1, paginaCheia(2000, corrida(1)));
            statusNaPagina(2, 429);

            StravaSyncResponseDto resposta = service.syncActivitiesForAtleta(atletaId, tenantId);

            assertThat(cursoresGravados()).isEmpty();
            assertThat(resposta.imported()).isEqualTo(1);
            assertThat(resposta.message()).contains("parcial").contains("próximo ciclo");
            verify(integracaoExternaRepository).atualizarStatusSync(
                    eq(integracaoId), eq(tenantId), any(), eq(1), eq(resposta.message()));
        }

        @Test
        @DisplayName("5xx no manual: grava o erro, relança, NÃO desativa e não salva a instância (token renovado preservado)")
        void manualComFalhaNaoDesativa() {
            integracaoComCursor(cursorDiasAtras(1));
            statusNaPagina(1, 503);

            assertThatThrownBy(() -> service.syncActivitiesForAtleta(atletaId, tenantId)).isInstanceOf(RuntimeException.class);

            assertThat(integracao.isAtivo()).isTrue();
            verify(integracaoExternaRepository, never()).save(any());
            verify(integracaoExternaRepository).atualizarStatusSync(eq(integracaoId), eq(tenantId), any(), any(), any());
        }

        @Test
        @DisplayName("QA — 503 depois de 2 inserções: status com a contagem real e mensagem segura, sem detalhe interno")
        void manualComFalhaDepoisDeProgresso() {
            integracaoComCursor(cursorDiasAtras(1));
            paginaDeQualquerFatia(1, paginaCheia(2000, corrida(1), corrida(2)));
            statusNaPagina(2, 503);

            assertThatThrownBy(() -> service.syncActivitiesForAtleta(atletaId, tenantId)).isInstanceOf(RuntimeException.class);

            ArgumentCaptor<String> erro = ArgumentCaptor.forClass(String.class);
            verify(integracaoExternaRepository).atualizarStatusSync(eq(integracaoId), eq(tenantId), any(), eq(2), erro.capture());
            assertThat(erro.getValue()).isEqualTo("Falha temporária na sincronização — nova tentativa no próximo ciclo")
                    .doesNotContain("localhost").doesNotContain("503 Service");
        }

        @Test
        @DisplayName("sync manual não usa o descarte (nem lê nem conta tentativa)")
        void manualNaoUsaDescarte() {
            integracaoComCursor(cursorDiasAtras(1));
            paginaDeQualquerFatia(1, pagina(corrida(1)));
            when(ingestaoTreinoRealizadoService.registrar(any(), eq("1"))).thenThrow(new IllegalStateException("erro"));

            assertThatThrownBy(() -> service.syncActivitiesForAtleta(atletaId, tenantId)).isInstanceOf(IllegalStateException.class);

            verifyNoInteractions(descarteWriter);
        }

        @Test
        @DisplayName("sync manual com cursor nulo parte de 90 dias sem gravar o horizonte")
        void manualSemCursorNaoGravaHorizonte() {
            integracaoComCursor(null);

            service.syncActivitiesForAtleta(atletaId, tenantId);

            assertThat(cursoresGravados()).isEmpty();
            verify(integracaoExternaRepository, times(1)).atualizarStatusSync(any(), any(), any(), any(), any());
        }
    }
}
