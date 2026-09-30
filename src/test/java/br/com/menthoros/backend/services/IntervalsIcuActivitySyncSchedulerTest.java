package br.com.menthoros.backend.services;

import br.com.menthoros.backend.config.external.IntervalsIcuProperties;
import br.com.menthoros.backend.dto.intervalsicu.IcuActivityDto;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.ErroCategoriaPull;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.ResultadoPull;
import br.com.menthoros.backend.exception.DomainConflictException;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.exception.IntervalsIcuApiException;
import br.com.menthoros.backend.exception.IntervalsIcuRateLimitException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.IntegracaoExternaRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.helper.IntervalsIcuActivityMapper;
import br.com.menthoros.backend.services.helper.PullResultado;
import br.com.menthoros.backend.services.helper.SyncDescarteWriter;
import br.com.menthoros.backend.services.helper.SyncPullLogWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pull agendado do intervals.icu sobre o cursor exclusivo {@code pull_cursor}
 * (fix-sync-cursor-data-loss, design D1/D2/D4/D5/D7). {@link IntervalsIcuProperties} entra como
 * instância real (é um POJO): os testes de janela e teto mudam os valores direto nela.
 *
 * <p>O cursor nunca é gravado por {@code save} da entidade — só por {@code atualizarPullCursor}; o
 * status, por {@code atualizarStatusSync}. Por isso os testes verificam essas duas chamadas.</p>
 */
@ExtendWith(MockitoExtension.class)
class IntervalsIcuActivitySyncSchedulerTest {

    private static final String EXTERNAL_ATHLETE = "i641775";
    private static final String TOKEN = "token-oauth";

    @Mock private IntegracaoExternaRepository integracaoExternaRepository;
    @Mock private TreinoRealizadoRepository treinoRealizadoRepository;
    @Mock private IntervalsIcuClient intervalsIcuClient;
    @Mock private IntervalsIcuActivityIngestionService ingestionService;
    @Mock private SyncPullLogWriter pullLogWriter;
    @Mock private SyncDescarteWriter descarteWriter;

    private IntervalsIcuProperties props;
    private IntervalsIcuActivitySyncScheduler scheduler;

    private UUID integracaoId;
    private UUID tenantId;
    private UUID atletaId;
    private Instant inicioDoTeste;

    @BeforeEach
    void setUp() {
        props = new IntervalsIcuProperties();
        // mapper real: é um helper puro sem dependências, e o filtro de modalidade é o que está em teste
        scheduler = new IntervalsIcuActivitySyncScheduler(
                integracaoExternaRepository, treinoRealizadoRepository, intervalsIcuClient,
                ingestionService, new IntervalsIcuActivityMapper(), props, pullLogWriter, descarteWriter);
        integracaoId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        atletaId = UUID.randomUUID();
        inicioDoTeste = Instant.now();
        // por padrão nada foi importado nem descartado, e todo import é inserção nova
        lenient().when(treinoRealizadoRepository.findByTenantIdAndFonteDadosAndExternalId(
                any(), eq(FonteDados.INTERVALS_ICU), anyString())).thenReturn(Optional.empty());
        lenient().when(descarteWriter.descartadas(any(), any(), eq(FonteDados.INTERVALS_ICU))).thenReturn(Set.of());
        lenient().when(ingestionService.importarAtividadeAgendada(any(), anyString(), any()))
                .thenReturn(new ImportacaoResultado(null, true));
        lenient().when(integracaoExternaRepository.atualizarPullCursor(any(), any(), any())).thenReturn(1);
        lenient().when(integracaoExternaRepository.atualizarStatusSync(any(), any(), any(), any(), any())).thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ----------------------------------------------------------------- fixtures

    private IntegracaoExterna integracao(UUID id, UUID atleta, UUID tenant, Instant pullCursor) {
        IntegracaoExterna i = new IntegracaoExterna();
        i.setId(id);
        i.setPlataforma(FonteDados.INTERVALS_ICU);
        i.setAtivo(true);
        i.setAutoSyncPausado(false);
        i.setTenantId(tenant);
        i.setExternalAthleteId(EXTERNAL_ATHLETE);
        i.setAccessToken(TOKEN);
        // ultimaSincronizacao "hoje" de propósito: é o que o push grava, e o pull não pode ler dele (CA1)
        i.setUltimaSincronizacao(Instant.now());
        i.setSyncActivityCount(0);
        // somente leitura no ORM — sem setter; no banco só atualizarPullCursor escreve
        ReflectionTestUtils.setField(i, "pullCursor", pullCursor);
        Atleta a = new Atleta();
        a.setId(atleta);
        Assessoria ass = new Assessoria();
        ass.setId(tenant);
        a.setAssessoria(ass);
        i.setAtleta(a);
        return i;
    }

    /** Lista o atleta no ciclo e devolve a mesma instância nas releituras (late-check e finalização). */
    private IntegracaoExterna atletaAtivo(Instant pullCursor) {
        IntegracaoExterna i = integracao(integracaoId, atletaId, tenantId, pullCursor);
        when(integracaoExternaRepository.findAllActiveByPlataforma(FonteDados.INTERVALS_ICU))
                .thenReturn(List.of(i));
        when(integracaoExternaRepository.findByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.INTERVALS_ICU, tenantId))
                .thenReturn(Optional.of(i));
        return i;
    }

    private static IcuActivityDto dto(String id, String startDateUtc) {
        return new IcuActivityDto(id, EXTERNAL_ATHLETE, "Run", "Corrida",
                startDateUtc.replace("Z", ""), startDateUtc,
                1800, 1850, 5000.0, null, null, null, null, null, null, null, null, null, null, null);
    }

    /** A API devolve em ordem DECRESCENTE (gate 0.2) — os fixtures simulam isso de propósito. */
    private void listagemDevolve(IcuActivityDto... desc) {
        when(intervalsIcuClient.listarAtividades(eq(TOKEN), eq(EXTERNAL_ATHLETE), any(), any()))
                .thenReturn(List.of(desc));
    }

    private List<String> idsImportadosEmOrdem() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(ingestionService, org.mockito.Mockito.atLeast(0))
                .importarAtividadeAgendada(eq(atletaId), captor.capture(), eq(tenantId));
        return captor.getAllValues();
    }

    /** Último valor gravado em pull_cursor por este ciclo (o horizonte inicial conta como gravação). */
    private Instant cursorGravado() {
        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(integracaoExternaRepository, org.mockito.Mockito.atLeastOnce())
                .atualizarPullCursor(eq(integracaoId), eq(tenantId), captor.capture());
        return captor.getValue();
    }

    private record Status(Instant ultima, Integer count, String erro) {}

    private Status statusGravado() {
        ArgumentCaptor<Instant> ultima = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Integer> count = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<String> erro = ArgumentCaptor.forClass(String.class);
        verify(integracaoExternaRepository).atualizarStatusSync(
                eq(integracaoId), eq(tenantId), ultima.capture(), count.capture(), erro.capture());
        return new Status(ultima.getValue(), count.getValue(), erro.getValue());
    }

    private PullResultado pullRegistrado() {
        ArgumentCaptor<PullResultado> captor = ArgumentCaptor.forClass(PullResultado.class);
        verify(pullLogWriter).registrar(eq(tenantId), eq(atletaId), eq(FonteDados.INTERVALS_ICU), captor.capture(), any());
        return captor.getValue();
    }

    private static final IcuActivityDto A1 = dto("i1", "2026-08-15T10:00:00Z");
    private static final IcuActivityDto A2 = dto("i2", "2026-08-16T10:00:00Z");
    private static final IcuActivityDto A3 = dto("i3", "2026-08-17T10:00:00Z");
    private static final Instant CURSOR = Instant.parse("2026-08-10T12:00:00Z");

    // ----------------------------------------------------------------- caminho feliz

    @Test
    @DisplayName("caminho feliz: importa em ordem cronológica, cursor vira agora, status e registro COMPLETO")
    void caminhoFeliz() {
        atletaAtivo(CURSOR);
        listagemDevolve(A3, A2, A1);

        scheduler.runDailyIncrementalSync();

        assertThat(idsImportadosEmOrdem()).containsExactly("i1", "i2", "i3");
        assertThat(cursorGravado()).isAfterOrEqualTo(inicioDoTeste);
        Status status = statusGravado();
        assertThat(status.ultima()).isAfterOrEqualTo(inicioDoTeste);
        assertThat(status.count()).isEqualTo(3);
        assertThat(status.erro()).isNull();
        assertThat(pullRegistrado()).isEqualTo(new PullResultado(ResultadoPull.COMPLETO, null, 3, 0));
        verify(integracaoExternaRepository, never()).save(any());
        assertThat(TenantContext.hasTenant()).isFalse();
    }

    // ----------------------------------------------------------------- late-check

    @Nested
    @DisplayName("late-check revalida ativo E autoSyncPausado com query fresca")
    class LateCheck {

        @Test
        void desativadoEntreAListagemEOProcessamentoEPulado() {
            IntegracaoExterna listada = integracao(integracaoId, atletaId, tenantId, CURSOR);
            IntegracaoExterna fresca = integracao(integracaoId, atletaId, tenantId, CURSOR);
            fresca.setAtivo(false);
            when(integracaoExternaRepository.findAllActiveByPlataforma(FonteDados.INTERVALS_ICU)).thenReturn(List.of(listada));
            when(integracaoExternaRepository.findByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.INTERVALS_ICU, tenantId))
                    .thenReturn(Optional.of(fresca));

            scheduler.runDailyIncrementalSync();

            verify(intervalsIcuClient, never()).listarAtividades(any(), any(), any(), any());
            verify(integracaoExternaRepository, never()).atualizarStatusSync(any(), any(), any(), any(), any());
            verify(pullLogWriter, never()).registrar(any(), any(), any(), any(), any());
        }

        @Test
        void pausadoEntreAListagemEOProcessamentoEPulado() {
            IntegracaoExterna listada = integracao(integracaoId, atletaId, tenantId, CURSOR);
            IntegracaoExterna fresca = integracao(integracaoId, atletaId, tenantId, CURSOR);
            fresca.setAutoSyncPausado(true);
            when(integracaoExternaRepository.findAllActiveByPlataforma(FonteDados.INTERVALS_ICU)).thenReturn(List.of(listada));
            when(integracaoExternaRepository.findByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.INTERVALS_ICU, tenantId))
                    .thenReturn(Optional.of(fresca));

            scheduler.runDailyIncrementalSync();

            verify(intervalsIcuClient, never()).listarAtividades(any(), any(), any(), any());
        }
    }

    // ----------------------------------------------------------------- janela e cursor (D1)

    @Nested
    @DisplayName("janela: pull_cursor menos overlap; horizonte inicial gravado antes de buscar")
    class Janela {

        @Test
        @DisplayName("CA1 — lista a partir de pull_cursor − overlap, ignorando ultimaSincronizacao (que o push adianta)")
        void parteDoPullCursorNaoDaUltimaSincronizacao() {
            props.setSyncOverlapDays(7);
            atletaAtivo(Instant.now().minus(Duration.ofDays(20)));
            listagemDevolve();

            scheduler.runDailyIncrementalSync();

            LocalDate hoje = LocalDate.now(ZoneOffset.UTC);
            verify(intervalsIcuClient).listarAtividades(TOKEN, EXTERNAL_ATHLETE, hoje.minusDays(27), hoje);
        }

        @Test
        @DisplayName("CA4 — cursor nulo: grava o horizonte inicial ANTES de listar")
        void horizonteInicialGravadoAntesDeListar() {
            props.setSyncDaysBack(30);
            atletaAtivo(null);
            List<String> ordem = new ArrayList<>();
            when(integracaoExternaRepository.atualizarPullCursor(eq(integracaoId), eq(tenantId), any()))
                    .thenAnswer(inv -> { ordem.add("cursor"); return 1; });
            when(intervalsIcuClient.listarAtividades(eq(TOKEN), eq(EXTERNAL_ATHLETE), any(), any()))
                    .thenAnswer(inv -> { ordem.add("listar"); throw new IntervalsIcuApiException(HttpStatus.TOO_MANY_REQUESTS, "429"); });

            scheduler.runDailyIncrementalSync();

            assertThat(ordem).containsExactly("cursor", "listar");
            Instant horizonte = cursorGravado();
            assertThat(horizonte).isBetween(inicioDoTeste.minus(Duration.ofDays(30)).minusSeconds(5),
                    Instant.now().minus(Duration.ofDays(30)));
            verify(intervalsIcuClient).listarAtividades(TOKEN, EXTERNAL_ATHLETE,
                    horizonte.atZone(ZoneOffset.UTC).toLocalDate().minusDays(props.getSyncOverlapDays()),
                    LocalDate.now(ZoneOffset.UTC));
            assertThat(pullRegistrado()).isEqualTo(new PullResultado(ResultadoPull.FALHA, ErroCategoriaPull.RATE_LIMIT, 0, 0));
        }

        @Test
        @DisplayName("CA4 — ciclo seguinte à falha parte do horizonte gravado, sem gravá-lo de novo")
        void cicloSeguinteUsaOMesmoHorizonte() {
            Instant horizonteGravado = Instant.now().minus(Duration.ofDays(91));
            atletaAtivo(horizonteGravado);
            when(intervalsIcuClient.listarAtividades(eq(TOKEN), eq(EXTERNAL_ATHLETE), any(), any()))
                    .thenThrow(new IntervalsIcuApiException(HttpStatus.TOO_MANY_REQUESTS, "429"));

            scheduler.runDailyIncrementalSync();

            verify(intervalsIcuClient).listarAtividades(TOKEN, EXTERNAL_ATHLETE,
                    horizonteGravado.atZone(ZoneOffset.UTC).toLocalDate().minusDays(props.getSyncOverlapDays()),
                    LocalDate.now(ZoneOffset.UTC));
            verify(integracaoExternaRepository, never()).atualizarPullCursor(any(), any(), any());
        }

        @Test
        @DisplayName("CA5 — cursor em D−120: backlog vai para a entrada agendada, sem limite de retroatividade")
        void backlogAntigoUsaAEntradaAgendada() {
            atletaAtivo(Instant.now().minus(Duration.ofDays(120)));
            IcuActivityDto antiga = dto("i-antiga", Instant.now().minus(Duration.ofDays(110)).toString());
            listagemDevolve(antiga);

            scheduler.runDailyIncrementalSync();

            verify(ingestionService).importarAtividadeAgendada(atletaId, "i-antiga", tenantId);
            verify(ingestionService, never()).importarAtividade(any(), anyString(), any());
            assertThat(pullRegistrado().insercoes()).isEqualTo(1);
        }
    }

    // ----------------------------------------------------------------- teto

    @Nested
    @DisplayName("teto por contagem (D4.1)")
    class Teto {

        @Test
        void importaSoAsNMaisAntigasEOCursorFicaNaUltimaProcessada() {
            props.setSyncMaxActivitiesPerCycle(2);
            atletaAtivo(CURSOR);
            listagemDevolve(A3, A2, A1);

            scheduler.runDailyIncrementalSync();

            assertThat(idsImportadosEmOrdem()).containsExactly("i1", "i2");
            assertThat(cursorGravado()).isEqualTo(Instant.parse("2026-08-16T10:00:00Z"));
            assertThat(statusGravado().erro()).isNull();
            // QA: sobrou trabalho na janela — COMPLETO aqui distorceria a métrica "0 faltantes em COMPLETO"
            assertThat(pullRegistrado()).isEqualTo(new PullResultado(ResultadoPull.PARCIAL, null, 2, 0));
        }

        @Test
        void jaImportadasNaoContamNoTetoNemGeramChamada() {
            props.setSyncMaxActivitiesPerCycle(2);
            atletaAtivo(CURSOR);
            listagemDevolve(A3, A2, A1);
            when(treinoRealizadoRepository.findByTenantIdAndFonteDadosAndExternalId(tenantId, FonteDados.INTERVALS_ICU, "i1"))
                    .thenReturn(Optional.of(new TreinoRealizado()));

            scheduler.runDailyIncrementalSync();

            // i1 já existia: sai antes do teto, então as 2 vagas vão para i2 e i3 — e a janela esgota
            assertThat(idsImportadosEmOrdem()).containsExactly("i2", "i3");
            assertThat(cursorGravado()).isAfterOrEqualTo(inicioDoTeste);
        }

        @Test
        @DisplayName("CA11 — descartadas saem ANTES do teto: 6 rejeições no overlap não travam a 7ª")
        void descartadasNaoConsomemOTeto() {
            props.setSyncMaxActivitiesPerCycle(6);
            atletaAtivo(CURSOR);
            List<IcuActivityDto> lista = new ArrayList<>();
            for (int d = 7; d >= 1; d--) {
                lista.add(dto("r" + d, "2026-08-1" + d + "T10:00:00Z"));
            }
            when(intervalsIcuClient.listarAtividades(eq(TOKEN), eq(EXTERNAL_ATHLETE), any(), any())).thenReturn(lista);
            when(descarteWriter.descartadas(tenantId, atletaId, FonteDados.INTERVALS_ICU))
                    .thenReturn(Set.of("r1", "r2", "r3", "r4", "r5", "r6"));

            scheduler.runDailyIncrementalSync();

            assertThat(idsImportadosEmOrdem()).containsExactly("r7");
        }
    }

    // ----------------------------------------------------------------- permanente (D2, D7)

    @Nested
    @DisplayName("falha permanente: descarte registrado, cursor passa, lote segue")
    class Permanente {

        @Test
        @DisplayName("422 registra descarte permanente, conta como ignorada e não aborta o lote")
        void regraVioladaEDescartada() {
            atletaAtivo(CURSOR);
            listagemDevolve(A3, A2, A1);
            doThrow(new DomainRuleViolationException("intervals.icu rejeitou a atividade"))
                    .when(ingestionService).importarAtividadeAgendada(atletaId, "i2", tenantId);

            scheduler.runDailyIncrementalSync();

            assertThat(idsImportadosEmOrdem()).containsExactly("i1", "i2", "i3");
            verify(descarteWriter).registrarPermanente(tenantId, atletaId, FonteDados.INTERVALS_ICU, "i2");
            assertThat(cursorGravado()).isAfterOrEqualTo(inicioDoTeste);
            assertThat(pullRegistrado())
                    .isEqualTo(new PullResultado(ResultadoPull.PARCIAL, ErroCategoriaPull.DADOS_INVALIDOS, 2, 1));
        }

        @Test
        @DisplayName("404 também é permanente")
        void notFoundTambemEPermanente() {
            atletaAtivo(CURSOR);
            listagemDevolve(A2, A1);
            doThrow(new DomainNotFoundException("Activity não encontrada"))
                    .when(ingestionService).importarAtividadeAgendada(atletaId, "i1", tenantId);

            scheduler.runDailyIncrementalSync();

            assertThat(idsImportadosEmOrdem()).containsExactly("i1", "i2");
            verify(descarteWriter).registrarPermanente(tenantId, atletaId, FonteDados.INTERVALS_ICU, "i1");
        }
    }

    // ----------------------------------------------------------------- transitória

    @Nested
    @DisplayName("falha transitória aborta o lote; cursor fica na última processada")
    class FalhaTransitoria {

        @Test
        void rateLimitNaSegundaNaoTentaATerceiraECursorFicaNaPrimeira() {
            atletaAtivo(CURSOR);
            listagemDevolve(A3, A2, A1);
            doThrow(new IntervalsIcuRateLimitException("429"))
                    .when(ingestionService).importarAtividadeAgendada(atletaId, "i2", tenantId);

            scheduler.runDailyIncrementalSync();

            assertThat(idsImportadosEmOrdem()).containsExactly("i1", "i2");
            assertThat(cursorGravado()).isEqualTo(Instant.parse("2026-08-15T10:00:00Z"));
            assertThat(statusGravado().erro()).contains("transitória");
            assertThat(pullRegistrado())
                    .isEqualTo(new PullResultado(ResultadoPull.PARCIAL, ErroCategoriaPull.TRANSITORIO, 1, 0));
        }

        @Test
        void falhaNaPrimeiraDeixaOCursorIntocado() {
            atletaAtivo(CURSOR);
            listagemDevolve(A2, A1);
            doThrow(new IntervalsIcuRateLimitException("429"))
                    .when(ingestionService).importarAtividadeAgendada(atletaId, "i1", tenantId);

            scheduler.runDailyIncrementalSync();

            assertThat(idsImportadosEmOrdem()).containsExactly("i1");
            verify(integracaoExternaRepository, never()).atualizarPullCursor(any(), any(), any());
            assertThat(statusGravado().erro()).contains("transitória");
            assertThat(pullRegistrado().resultado()).isEqualTo(ResultadoPull.FALHA);
        }

        @Test
        void conflitoCrossFonteStravaEFalhaDeAtletaNaoDeAtividade() {
            atletaAtivo(CURSOR);
            listagemDevolve(A3, A2, A1);
            doThrow(new DomainConflictException("pause a sincronização Strava deste atleta"))
                    .when(ingestionService).importarAtividadeAgendada(atletaId, "i2", tenantId);

            scheduler.runDailyIncrementalSync();

            assertThat(idsImportadosEmOrdem()).containsExactly("i1", "i2");
            assertThat(cursorGravado()).isEqualTo(Instant.parse("2026-08-15T10:00:00Z"));
            assertThat(pullRegistrado().erro()).isEqualTo(ErroCategoriaPull.CONFLITO);
        }
    }

    // ----------------------------------------------------------------- inesperada (D5, D7)

    @Nested
    @DisplayName("exceção inesperada numa atividade: preserva o progresso e conta tentativa")
    class Inesperada {

        @Test
        @DisplayName("CA8 — 2 inserções + inesperada na 3ª → PARCIAL/INESPERADO com 2; cursor na 2ª")
        void preservaProgresso() {
            atletaAtivo(CURSOR);
            listagemDevolve(A3, A2, A1);
            when(descarteWriter.registrarFalha(tenantId, atletaId, FonteDados.INTERVALS_ICU, "i3")).thenReturn(false);
            doThrow(new IllegalStateException("NPE no mapper"))
                    .when(ingestionService).importarAtividadeAgendada(atletaId, "i3", tenantId);

            scheduler.runDailyIncrementalSync();

            assertThat(cursorGravado()).isEqualTo(Instant.parse("2026-08-16T10:00:00Z"));
            assertThat(pullRegistrado())
                    .isEqualTo(new PullResultado(ResultadoPull.PARCIAL, ErroCategoriaPull.INESPERADO, 2, 0));
            assertThat(statusGravado().count()).isEqualTo(2);
        }

        @Test
        @DisplayName("CA12 — na 3ª tentativa a atividade é descartada, contada em ignoradas, e o lote segue")
        void terceiraTentativaDescartaESegue() {
            atletaAtivo(CURSOR);
            listagemDevolve(A3, A2, A1);
            when(descarteWriter.registrarFalha(tenantId, atletaId, FonteDados.INTERVALS_ICU, "i2")).thenReturn(true);
            doThrow(new IllegalStateException("erro determinístico"))
                    .when(ingestionService).importarAtividadeAgendada(atletaId, "i2", tenantId);

            scheduler.runDailyIncrementalSync();

            assertThat(idsImportadosEmOrdem()).containsExactly("i1", "i2", "i3");
            assertThat(cursorGravado()).isAfterOrEqualTo(inicioDoTeste);
            assertThat(pullRegistrado())
                    .isEqualTo(new PullResultado(ResultadoPull.PARCIAL, ErroCategoriaPull.INESPERADO, 2, 1));
        }

        @Test
        @DisplayName("QA — falha de banco (deadlock) é transitória: não conta tentativa nem descarta")
        void falhaDeBancoNaoContaTentativa() {
            atletaAtivo(CURSOR);
            listagemDevolve(A2, A1);
            doThrow(new org.springframework.dao.CannotAcquireLockException("deadlock"))
                    .when(ingestionService).importarAtividadeAgendada(atletaId, "i2", tenantId);

            scheduler.runDailyIncrementalSync();

            verify(descarteWriter, never()).registrarFalha(any(), any(), any(), any());
            assertThat(cursorGravado()).isEqualTo(Instant.parse("2026-08-15T10:00:00Z"));
            assertThat(pullRegistrado())
                    .isEqualTo(new PullResultado(ResultadoPull.PARCIAL, ErroCategoriaPull.TRANSITORIO, 1, 0));
        }

        @Test
        @DisplayName("CA8 — 2 inserções + falha ao gravar o cursor → PARCIAL com 2, não FALHA/0")
        void falhaNaFinalizacaoPreservaContagem() {
            atletaAtivo(CURSOR);
            listagemDevolve(A2, A1);
            when(integracaoExternaRepository.atualizarPullCursor(eq(integracaoId), eq(tenantId), any()))
                    .thenThrow(new IllegalStateException("conexão caiu"));

            scheduler.runDailyIncrementalSync();

            assertThat(pullRegistrado())
                    .isEqualTo(new PullResultado(ResultadoPull.PARCIAL, ErroCategoriaPull.INESPERADO, 2, 0));
        }
    }

    // ----------------------------------------------------------------- listagem

    @Test
    @DisplayName("listagem falha (401): FALHA/CREDENCIAL, erro no status, cursor intocado, os demais seguem")
    void falhaNaListagemNaoAbortaOCiclo() {
        UUID outraIntegracao = UUID.randomUUID();
        UUID outroAtleta = UUID.randomUUID();
        IntegracaoExterna a = integracao(integracaoId, atletaId, tenantId, CURSOR);
        IntegracaoExterna b = integracao(outraIntegracao, outroAtleta, tenantId, CURSOR);
        b.setExternalAthleteId("i999");
        Instant ultimaAnterior = a.getUltimaSincronizacao();
        when(integracaoExternaRepository.findAllActiveByPlataforma(FonteDados.INTERVALS_ICU)).thenReturn(List.of(a, b));
        when(integracaoExternaRepository.findByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.INTERVALS_ICU, tenantId))
                .thenReturn(Optional.of(a));
        when(integracaoExternaRepository.findByAtletaIdAndPlataformaAndTenantId(outroAtleta, FonteDados.INTERVALS_ICU, tenantId))
                .thenReturn(Optional.of(b));
        when(intervalsIcuClient.listarAtividades(eq(TOKEN), eq(EXTERNAL_ATHLETE), any(), any()))
                .thenThrow(new IntervalsIcuApiException(HttpStatus.UNAUTHORIZED, "intervals.icu retornou 401 ao listar atividades"));
        when(intervalsIcuClient.listarAtividades(eq(TOKEN), eq("i999"), any(), any())).thenReturn(List.of(A1));

        scheduler.runDailyIncrementalSync();

        verify(ingestionService).importarAtividadeAgendada(outroAtleta, "i1", tenantId);
        verify(integracaoExternaRepository, never()).atualizarPullCursor(eq(integracaoId), any(), any());
        Status status = statusGravado();
        assertThat(status.erro()).contains("401");
        // FALHA não finge sincronização: ultimaSincronizacao fica onde estava
        assertThat(status.ultima()).isEqualTo(ultimaAnterior);
        assertThat(pullRegistrado()).isEqualTo(new PullResultado(ResultadoPull.FALHA, ErroCategoriaPull.CREDENCIAL, 0, 0));
        assertThat(TenantContext.hasTenant()).isFalse();
    }

    // ----------------------------------------------------------------- desconexão

    @Test
    @DisplayName("desconexão durante o lote: a releitura encontra ativo=false e nem cursor nem status são gravados")
    void desconexaoDuranteOLote() {
        IntegracaoExterna ativa = integracao(integracaoId, atletaId, tenantId, CURSOR);
        IntegracaoExterna desconectada = integracao(integracaoId, atletaId, tenantId, CURSOR);
        desconectada.setAtivo(false);
        when(integracaoExternaRepository.findAllActiveByPlataforma(FonteDados.INTERVALS_ICU)).thenReturn(List.of(ativa));
        when(integracaoExternaRepository.findByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.INTERVALS_ICU, tenantId))
                .thenReturn(Optional.of(ativa), Optional.of(desconectada));
        listagemDevolve(A1);

        scheduler.runDailyIncrementalSync();

        verify(ingestionService).importarAtividadeAgendada(atletaId, "i1", tenantId);
        verify(integracaoExternaRepository, never()).atualizarPullCursor(any(), any(), any());
        verify(integracaoExternaRepository, never()).atualizarStatusSync(any(), any(), any(), any(), any());
        verify(integracaoExternaRepository, never()).save(any());
    }

    // ----------------------------------------------------------------- contagem

    @Test
    @DisplayName("contagem vem de inserida: import concorrente ou já existente não infla o syncActivityCount")
    void contadorPorInserida() {
        IntegracaoExterna i = atletaAtivo(CURSOR);
        i.setSyncActivityCount(5);
        listagemDevolve(A2, A1);
        when(ingestionService.importarAtividadeAgendada(atletaId, "i1", tenantId))
                .thenReturn(new ImportacaoResultado(null, false));

        scheduler.runDailyIncrementalSync();

        assertThat(idsImportadosEmOrdem()).hasSize(2);
        assertThat(statusGravado().count()).isEqualTo(6);
        assertThat(pullRegistrado().insercoes()).isEqualTo(1);
    }

    // ----------------------------------------------------------------- multi-tenancy

    @Test
    @DisplayName("multi-tenancy: cada import roda com o TenantContext do próprio atleta, limpo ao final")
    void tenantContextPorAtleta() {
        UUID tenantB = UUID.randomUUID();
        UUID atletaB = UUID.randomUUID();
        IntegracaoExterna a = integracao(integracaoId, atletaId, tenantId, CURSOR);
        IntegracaoExterna b = integracao(UUID.randomUUID(), atletaB, tenantB, CURSOR);
        b.setExternalAthleteId("i999");
        when(integracaoExternaRepository.findAllActiveByPlataforma(FonteDados.INTERVALS_ICU)).thenReturn(List.of(a, b));
        when(integracaoExternaRepository.findByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.INTERVALS_ICU, tenantId))
                .thenReturn(Optional.of(a));
        when(integracaoExternaRepository.findByAtletaIdAndPlataformaAndTenantId(atletaB, FonteDados.INTERVALS_ICU, tenantB))
                .thenReturn(Optional.of(b));
        when(intervalsIcuClient.listarAtividades(eq(TOKEN), eq(EXTERNAL_ATHLETE), any(), any())).thenReturn(List.of(A1));
        when(intervalsIcuClient.listarAtividades(eq(TOKEN), eq("i999"), any(), any())).thenReturn(List.of(A2));
        List<UUID> tenantsVistos = new ArrayList<>();
        doAnswer(inv -> { tenantsVistos.add(TenantContext.getTenantId()); return new ImportacaoResultado(null, true); })
                .when(ingestionService).importarAtividadeAgendada(any(), anyString(), any());

        scheduler.runDailyIncrementalSync();

        assertThat(tenantsVistos).containsExactly(tenantId, tenantB);
        verify(pullLogWriter).registrar(eq(tenantB), eq(atletaB), eq(FonteDados.INTERVALS_ICU), any(), any());
        assertThat(TenantContext.hasTenant()).isFalse();
    }

    @Test
    @DisplayName("idempotência: já importada nem chega à ingestão — o scheduler não contorna o dedup")
    void jaImportadaNaoEReimportada() {
        atletaAtivo(CURSOR);
        listagemDevolve(A1);
        when(treinoRealizadoRepository.findByTenantIdAndFonteDadosAndExternalId(tenantId, FonteDados.INTERVALS_ICU, "i1"))
                .thenReturn(Optional.of(new TreinoRealizado()));

        scheduler.runDailyIncrementalSync();

        verify(ingestionService, never()).importarAtividadeAgendada(any(), anyString(), any());
        assertThat(cursorGravado()).isAfterOrEqualTo(inicioDoTeste);
        assertThat(pullRegistrado()).isEqualTo(new PullResultado(ResultadoPull.COMPLETO, null, 0, 0));
    }

    @Test
    @DisplayName("QA — rede do catch externo grava o erro por UPDATE pontual, nunca save da entidade (D0)")
    void registrarErroSemSave() {
        IntegracaoExterna i = integracao(integracaoId, atletaId, tenantId, CURSOR);
        when(integracaoExternaRepository.findAllActiveByPlataforma(FonteDados.INTERVALS_ICU)).thenReturn(List.of(i));
        when(integracaoExternaRepository.findByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.INTERVALS_ICU, tenantId))
                .thenThrow(new IllegalStateException("falha no late-check"))
                .thenReturn(Optional.of(i));

        scheduler.runDailyIncrementalSync();

        verify(integracaoExternaRepository, never()).save(any());
        Status status = statusGravado();
        assertThat(status.erro()).isEqualTo("Falha inesperada no sync (IllegalStateException)");
        assertThat(status.ultima()).isEqualTo(i.getUltimaSincronizacao());
    }

    @Test
    @DisplayName("falha ao gravar o registro de pull não derruba o ciclo dos demais atletas")
    void falhaNoRegistroNaoDerrubaOCiclo() {
        atletaAtivo(CURSOR);
        listagemDevolve(A1);
        doThrow(new IllegalStateException("banco fora")).when(pullLogWriter)
                .registrar(any(), any(), any(), any(), any());

        scheduler.runDailyIncrementalSync();

        verify(ingestionService).importarAtividadeAgendada(atletaId, "i1", tenantId);
        assertThat(TenantContext.hasTenant()).isFalse();
    }

    // ----------------------------------------------------------------- robustez

    @Nested
    @DisplayName("robustez do ciclo")
    class Robustez {

        @Test
        @DisplayName("atividade sem start_date é pulada, contada em ignoradas e deixa o pull PARCIAL/DADOS_INVALIDOS")
        void startDateNuloEPuladoEContado() {
            atletaAtivo(CURSOR);
            IcuActivityDto semData = new IcuActivityDto("i-sem-data", EXTERNAL_ATHLETE, "Run", "Corrida",
                    "2026-08-16T07:00:00", null,
                    1800, 1850, 5000.0, null, null, null, null, null, null, null, null, null, null, null);
            listagemDevolve(A2, semData, A1);

            scheduler.runDailyIncrementalSync();

            assertThat(idsImportadosEmOrdem()).containsExactly("i1", "i2");
            assertThat(cursorGravado()).isAfterOrEqualTo(inicioDoTeste);
            assertThat(pullRegistrado())
                    .isEqualTo(new PullResultado(ResultadoPull.PARCIAL, ErroCategoriaPull.DADOS_INVALIDOS, 2, 1));
            // QA: o coach precisa saber que houve atividade não importada
            assertThat(statusGravado().erro()).contains("1 atividade(s) não importada(s)");
        }

        @Test
        @DisplayName("modalidade não suportada é descartada pela listagem — sem fetch, sem vaga no teto")
        void modalidadeNaoSuportadaNaoEBuscada() {
            props.setSyncMaxActivitiesPerCycle(2);
            atletaAtivo(CURSOR);
            IcuActivityDto natacao = new IcuActivityDto("i-swim", EXTERNAL_ATHLETE, "Swim", "Natação",
                    "2026-08-14T07:00:00", "2026-08-14T10:00:00Z",
                    1800, 1850, 1500.0, null, null, null, null, null, null, null, null, null, null, null);
            listagemDevolve(A2, A1, natacao);

            scheduler.runDailyIncrementalSync();

            assertThat(idsImportadosEmOrdem()).containsExactly("i1", "i2");
            verify(ingestionService, never()).importarAtividadeAgendada(any(), eq("i-swim"), any());
            assertThat(pullRegistrado().ignoradas()).isZero();
        }

        @Test
        @DisplayName("exceção inesperada na listagem grava mensagem genérica, nunca o detalhe interno")
        void excecaoInesperadaNaoVazaDetalhe() {
            atletaAtivo(CURSOR);
            when(intervalsIcuClient.listarAtividades(eq(TOKEN), eq(EXTERNAL_ATHLETE), any(), any()))
                    .thenThrow(new IllegalStateException("select * from tb_segredo where " + "x".repeat(600)));

            scheduler.runDailyIncrementalSync();

            assertThat(statusGravado().erro())
                    .isEqualTo("Falha inesperada no sync (IllegalStateException)")
                    .doesNotContain("tb_segredo");
            assertThat(pullRegistrado()).isEqualTo(new PullResultado(ResultadoPull.FALHA, ErroCategoriaPull.INESPERADO, 0, 0));
            assertThat(TenantContext.hasTenant()).isFalse();
        }

        @Test
        @DisplayName("mensagem conhecida acima de 500 caracteres é cortada — o registro do erro não pode falhar")
        void mensagemLongaECortadaEm500() {
            String longa = "intervals.icu retornou 503 ao listar atividades: " + "detalhe ".repeat(100);
            String segura = IntervalsIcuActivitySyncScheduler.mensagemSegura(new IntervalsIcuApiException(null, longa));

            assertThat(segura).hasSize(500).startsWith("intervals.icu retornou 503");
        }

        @Test
        @DisplayName("calcularCursor: os três ramos, sem o resto do ciclo")
        void regraDoCursorIsolada() {
            Instant anterior = Instant.parse("2026-08-01T00:00:00Z");
            Instant ultima = Instant.parse("2026-08-15T10:00:00Z");

            var cruzeiro = IntervalsIcuActivitySyncScheduler.calcularCursor(false, true, ultima, anterior);
            var parcial = IntervalsIcuActivitySyncScheduler.calcularCursor(false, false, ultima, anterior);
            var transitoriaComProgresso = IntervalsIcuActivitySyncScheduler.calcularCursor(true, true, ultima, anterior);
            var transitoriaNaPrimeira = IntervalsIcuActivitySyncScheduler.calcularCursor(true, false, null, anterior);

            assertThat(cruzeiro.cursor()).isAfterOrEqualTo(inicioDoTeste);
            assertThat(cruzeiro.erro()).isNull();
            assertThat(parcial.cursor()).isEqualTo(ultima);
            assertThat(parcial.erro()).isNull();
            assertThat(transitoriaComProgresso.cursor()).isEqualTo(ultima);
            assertThat(transitoriaComProgresso.erro()).contains("transitória");
            assertThat(transitoriaNaPrimeira.cursor()).isEqualTo(anterior);
            assertThat(transitoriaNaPrimeira.erro()).contains("mantido");
        }
    }
}
