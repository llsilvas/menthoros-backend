package br.com.menthoros.backend.services;

import br.com.menthoros.backend.config.external.IntervalsIcuProperties;
import br.com.menthoros.backend.dto.intervalsicu.IcuActivityDto;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.exception.DomainConflictException;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.exception.IntervalsIcuApiException;
import br.com.menthoros.backend.exception.IntervalsIcuRateLimitException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.IntegracaoExternaRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Espelha {@link StravaActivitySyncSchedulerTest}. {@link IntervalsIcuProperties} entra como
 * instância real (é um POJO): os testes de janela e teto mudam os valores direto nela.
 */
@ExtendWith(MockitoExtension.class)
class IntervalsIcuActivitySyncSchedulerTest {

    private static final String EXTERNAL_ATHLETE = "i641775";
    private static final String TOKEN = "token-oauth";

    @Mock private IntegracaoExternaRepository integracaoExternaRepository;
    @Mock private TreinoRealizadoRepository treinoRealizadoRepository;
    @Mock private IntervalsIcuClient intervalsIcuClient;
    @Mock private IntervalsIcuActivityIngestionService ingestionService;

    private IntervalsIcuProperties props;
    private IntervalsIcuActivitySyncScheduler scheduler;

    private UUID tenantId;
    private UUID atletaId;
    private Instant inicioDoTeste;

    @BeforeEach
    void setUp() {
        props = new IntervalsIcuProperties();
        scheduler = new IntervalsIcuActivitySyncScheduler(
                integracaoExternaRepository, treinoRealizadoRepository, intervalsIcuClient,
                ingestionService, props);
        tenantId = UUID.randomUUID();
        atletaId = UUID.randomUUID();
        inicioDoTeste = Instant.now();
        // por padrão nada foi importado ainda e a contagem não muda — cada teste sobrescreve o que precisa
        lenient().when(treinoRealizadoRepository.findByTenantIdAndFonteDadosAndExternalId(
                any(), eq(FonteDados.INTERVALS_ICU), anyString())).thenReturn(Optional.empty());
        lenient().when(treinoRealizadoRepository.countByTenantIdAndAtletaIdAndFonteDados(
                any(), any(), eq(FonteDados.INTERVALS_ICU))).thenReturn(0L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ----------------------------------------------------------------- fixtures

    private IntegracaoExterna integracao(UUID atleta, UUID tenant, Instant ultimaSincronizacao) {
        IntegracaoExterna i = new IntegracaoExterna();
        i.setPlataforma(FonteDados.INTERVALS_ICU);
        i.setAtivo(true);
        i.setAutoSyncPausado(false);
        i.setTenantId(tenant);
        i.setExternalAthleteId(EXTERNAL_ATHLETE);
        i.setAccessToken(TOKEN);
        i.setUltimaSincronizacao(ultimaSincronizacao);
        i.setSyncActivityCount(0);
        Atleta a = new Atleta();
        a.setId(atleta);
        Assessoria ass = new Assessoria();
        ass.setId(tenant);
        a.setAssessoria(ass);
        i.setAtleta(a);
        return i;
    }

    /** Lista o atleta no ciclo e devolve a mesma instância nas releituras (late-check e pré-save). */
    private IntegracaoExterna atletaAtivo(Instant ultimaSincronizacao) {
        IntegracaoExterna i = integracao(atletaId, tenantId, ultimaSincronizacao);
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
        verify(ingestionService, org.mockito.Mockito.atLeast(0)).importarAtividade(eq(atletaId), captor.capture(), eq(tenantId));
        return captor.getAllValues();
    }

    private IntegracaoExterna salva() {
        ArgumentCaptor<IntegracaoExterna> captor = ArgumentCaptor.forClass(IntegracaoExterna.class);
        verify(integracaoExternaRepository).save(captor.capture());
        return captor.getValue();
    }

    private static final IcuActivityDto A1 = dto("i1", "2026-08-15T10:00:00Z");
    private static final IcuActivityDto A2 = dto("i2", "2026-08-16T10:00:00Z");
    private static final IcuActivityDto A3 = dto("i3", "2026-08-17T10:00:00Z");

    // ----------------------------------------------------------------- 2.0 — CA1

    @Test
    @DisplayName("2.0 caminho feliz: importa na ordem cronológica, cursor vira agora, sem erro, contador soma as novas")
    void caminhoFeliz() {
        atletaAtivo(null);
        listagemDevolve(A3, A2, A1);
        when(treinoRealizadoRepository.countByTenantIdAndAtletaIdAndFonteDados(tenantId, atletaId, FonteDados.INTERVALS_ICU))
                .thenReturn(0L, 3L);

        scheduler.runDailyIncrementalSync();

        assertThat(idsImportadosEmOrdem()).containsExactly("i1", "i2", "i3");
        IntegracaoExterna salva = salva();
        assertThat(salva.getUltimaSincronizacao()).isAfterOrEqualTo(inicioDoTeste);
        assertThat(salva.getLastSyncError()).isNull();
        assertThat(salva.getSyncActivityCount()).isEqualTo(3);
        assertThat(TenantContext.hasTenant()).isFalse();
    }

    // ----------------------------------------------------------------- 2.1 — CA3

    @Nested
    @DisplayName("2.1 late-check revalida ativo E autoSyncPausado com query fresca")
    class LateCheck {

        @Test
        void desativadoEntreAListagemEOProcessamentoEPulado() {
            IntegracaoExterna listada = integracao(atletaId, tenantId, null);
            IntegracaoExterna fresca = integracao(atletaId, tenantId, null);
            fresca.setAtivo(false);
            when(integracaoExternaRepository.findAllActiveByPlataforma(FonteDados.INTERVALS_ICU)).thenReturn(List.of(listada));
            when(integracaoExternaRepository.findByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.INTERVALS_ICU, tenantId))
                    .thenReturn(Optional.of(fresca));

            scheduler.runDailyIncrementalSync();

            verify(intervalsIcuClient, never()).listarAtividades(any(), any(), any(), any());
            verify(integracaoExternaRepository, never()).save(any());
        }

        @Test
        void pausadoEntreAListagemEOProcessamentoEPulado() {
            IntegracaoExterna listada = integracao(atletaId, tenantId, null);
            IntegracaoExterna fresca = integracao(atletaId, tenantId, null);
            fresca.setAutoSyncPausado(true);
            when(integracaoExternaRepository.findAllActiveByPlataforma(FonteDados.INTERVALS_ICU)).thenReturn(List.of(listada));
            when(integracaoExternaRepository.findByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.INTERVALS_ICU, tenantId))
                    .thenReturn(Optional.of(fresca));

            scheduler.runDailyIncrementalSync();

            verify(intervalsIcuClient, never()).listarAtividades(any(), any(), any(), any());
        }
    }

    // ----------------------------------------------------------------- 2.2 — CA6 / CA7 (janela)

    @Nested
    @DisplayName("2.2 janela: cursor menos overlap, ou fallback de syncDaysBack no primeiro ciclo")
    class Janela {

        @Test
        void cicloSubsequenteParteDoCursorMenosOverlap() {
            props.setSyncOverlapDays(2);
            atletaAtivo(Instant.parse("2026-08-10T12:00:00Z"));
            listagemDevolve();

            scheduler.runDailyIncrementalSync();

            verify(intervalsIcuClient).listarAtividades(TOKEN, EXTERNAL_ATHLETE,
                    LocalDate.of(2026, 8, 8), LocalDate.now(ZoneOffset.UTC));
        }

        @Test
        void primeiroCicloUsaOFallbackDeDias() {
            props.setSyncDaysBack(30);
            atletaAtivo(null);
            listagemDevolve();

            scheduler.runDailyIncrementalSync();

            verify(intervalsIcuClient).listarAtividades(TOKEN, EXTERNAL_ATHLETE,
                    LocalDate.now(ZoneOffset.UTC).minusDays(30), LocalDate.now(ZoneOffset.UTC));
        }
    }

    // ----------------------------------------------------------------- 2.2b — CA7 (teto)

    @Nested
    @DisplayName("2.2b teto por contagem (D4.1)")
    class Teto {

        @Test
        void importaSoAsNMaisAntigasEOCursorFicaNaUltimaProcessada() {
            props.setSyncMaxActivitiesPerCycle(2);
            atletaAtivo(null);
            listagemDevolve(A3, A2, A1);

            scheduler.runDailyIncrementalSync();

            assertThat(idsImportadosEmOrdem()).containsExactly("i1", "i2");
            assertThat(salva().getUltimaSincronizacao()).isEqualTo(Instant.parse("2026-08-16T10:00:00Z"));
            assertThat(salva().getLastSyncError()).isNull();
        }

        @Test
        void jaImportadasNaoContamNoTetoNemGeramChamada() {
            props.setSyncMaxActivitiesPerCycle(2);
            atletaAtivo(null);
            listagemDevolve(A3, A2, A1);
            when(treinoRealizadoRepository.findByTenantIdAndFonteDadosAndExternalId(tenantId, FonteDados.INTERVALS_ICU, "i1"))
                    .thenReturn(Optional.of(new TreinoRealizado()));

            scheduler.runDailyIncrementalSync();

            // i1 já existia: sai antes do teto, então as 2 vagas vão para i2 e i3 — e a janela esgota
            assertThat(idsImportadosEmOrdem()).containsExactly("i2", "i3");
            assertThat(salva().getUltimaSincronizacao()).isAfterOrEqualTo(inicioDoTeste);
        }

        @Test
        void pendentesDentroDoTetoEsgotamAJanelaECursorViraAgora() {
            props.setSyncMaxActivitiesPerCycle(6);
            atletaAtivo(Instant.parse("2026-08-01T00:00:00Z"));
            listagemDevolve(A2, A1);

            scheduler.runDailyIncrementalSync();

            assertThat(idsImportadosEmOrdem()).containsExactly("i1", "i2");
            assertThat(salva().getUltimaSincronizacao()).isAfterOrEqualTo(inicioDoTeste);
        }
    }

    // ----------------------------------------------------------------- 2.3 — CA4

    @Test
    @DisplayName("2.3 falha PERMANENTE de uma atividade não aborta o lote e o cursor avança normalmente")
    void falhaPermanenteIsolada() {
        atletaAtivo(null);
        listagemDevolve(A3, A2, A1);
        // lenient: strict stubs lançariam PotentialStubbingProblem na chamada com OUTRO id
        lenient().doThrow(new DomainRuleViolationException("Modalidade não suportada"))
                .when(ingestionService).importarAtividade(atletaId, "i2", tenantId);

        scheduler.runDailyIncrementalSync();

        assertThat(idsImportadosEmOrdem()).containsExactly("i1", "i2", "i3");
        IntegracaoExterna salva = salva();
        assertThat(salva.getUltimaSincronizacao()).isAfterOrEqualTo(inicioDoTeste);
        assertThat(salva.getLastSyncError()).isNull();
    }

    @Test
    @DisplayName("2.3 DomainNotFoundException também é permanente")
    void notFoundTambemEPermanente() {
        atletaAtivo(null);
        listagemDevolve(A2, A1);
        // lenient: strict stubs lançariam PotentialStubbingProblem na chamada com OUTRO id
        lenient().doThrow(new DomainNotFoundException("Activity não encontrada"))
                .when(ingestionService).importarAtividade(atletaId, "i1", tenantId);

        scheduler.runDailyIncrementalSync();

        assertThat(idsImportadosEmOrdem()).containsExactly("i1", "i2");
        assertThat(salva().getUltimaSincronizacao()).isAfterOrEqualTo(inicioDoTeste);
    }

    // ----------------------------------------------------------------- 2.4 / 2.5 — CA10 / CA12

    @Nested
    @DisplayName("2.4/2.5 falha TRANSITÓRIA aborta o lote; cursor fica na última processada")
    class FalhaTransitoria {

        @Test
        void rateLimitNaSegundaNaoTentaATerceiraECursorFicaNaPrimeira() {
            atletaAtivo(null);
            listagemDevolve(A3, A2, A1);
            // lenient: strict stubs lançariam PotentialStubbingProblem na chamada com OUTRO id
        lenient().doThrow(new IntervalsIcuRateLimitException("429"))
                    .when(ingestionService).importarAtividade(atletaId, "i2", tenantId);

            scheduler.runDailyIncrementalSync();

            assertThat(idsImportadosEmOrdem()).containsExactly("i1", "i2");
            IntegracaoExterna salva = salva();
            assertThat(salva.getUltimaSincronizacao()).isEqualTo(Instant.parse("2026-08-15T10:00:00Z"));
            assertThat(salva.getLastSyncError()).contains("transitória");
        }

        @Test
        void falhaNaPrimeiraDeixaOCursorIntocado() {
            Instant cursorAnterior = Instant.parse("2026-08-01T00:00:00Z");
            atletaAtivo(cursorAnterior);
            listagemDevolve(A2, A1);
            // lenient: strict stubs lançariam PotentialStubbingProblem na chamada com OUTRO id
        lenient().doThrow(new IntervalsIcuRateLimitException("429"))
                    .when(ingestionService).importarAtividade(atletaId, "i1", tenantId);

            scheduler.runDailyIncrementalSync();

            assertThat(idsImportadosEmOrdem()).containsExactly("i1");
            IntegracaoExterna salva = salva();
            assertThat(salva.getUltimaSincronizacao()).isEqualTo(cursorAnterior);
            assertThat(salva.getLastSyncError()).contains("transitória");
        }

        @Test
        void conflitoCrossFonteStravaEFalhaDeAtletaNaoDeAtividade() {
            atletaAtivo(null);
            listagemDevolve(A3, A2, A1);
            // lenient: strict stubs lançariam PotentialStubbingProblem na chamada com OUTRO id
        lenient().doThrow(new DomainConflictException("pause a sincronização Strava deste atleta"))
                    .when(ingestionService).importarAtividade(atletaId, "i2", tenantId);

            scheduler.runDailyIncrementalSync();

            assertThat(idsImportadosEmOrdem()).containsExactly("i1", "i2");
            assertThat(salva().getUltimaSincronizacao()).isEqualTo(Instant.parse("2026-08-15T10:00:00Z"));
        }
    }

    // ----------------------------------------------------------------- 2.6 — CA5 / CA9

    @Test
    @DisplayName("2.6 listagem falha para um atleta: grava lastSyncError via reload, cursor intocado, os demais seguem")
    void falhaNaListagemNaoAbortaOCicloEGravaOErro() {
        UUID outroAtleta = UUID.randomUUID();
        IntegracaoExterna a = integracao(atletaId, tenantId, Instant.parse("2026-08-01T00:00:00Z"));
        IntegracaoExterna b = integracao(outroAtleta, tenantId, null);
        b.setExternalAthleteId("i999");
        when(integracaoExternaRepository.findAllActiveByPlataforma(FonteDados.INTERVALS_ICU)).thenReturn(List.of(a, b));
        when(integracaoExternaRepository.findByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.INTERVALS_ICU, tenantId))
                .thenReturn(Optional.of(a));
        when(integracaoExternaRepository.findByAtletaIdAndPlataformaAndTenantId(outroAtleta, FonteDados.INTERVALS_ICU, tenantId))
                .thenReturn(Optional.of(b));
        when(intervalsIcuClient.listarAtividades(eq(TOKEN), eq(EXTERNAL_ATHLETE), any(), any()))
                .thenThrow(new IntervalsIcuApiException(HttpStatus.UNAUTHORIZED, "intervals.icu retornou 401 ao listar atividades"));
        when(intervalsIcuClient.listarAtividades(eq(TOKEN), eq("i999"), any(), any())).thenReturn(List.of(A1));

        scheduler.runDailyIncrementalSync();

        verify(ingestionService).importarAtividade(outroAtleta, "i1", tenantId);
        ArgumentCaptor<IntegracaoExterna> captor = ArgumentCaptor.forClass(IntegracaoExterna.class);
        verify(integracaoExternaRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        IntegracaoExterna aSalva = captor.getAllValues().stream()
                .filter(i -> i.getAtleta().getId().equals(atletaId)).findFirst().orElseThrow();
        assertThat(aSalva.getLastSyncError()).contains("401");
        assertThat(aSalva.getUltimaSincronizacao()).isEqualTo(Instant.parse("2026-08-01T00:00:00Z"));
        assertThat(TenantContext.hasTenant()).isFalse();
    }

    // ----------------------------------------------------------------- 2.7 — CA11

    @Test
    @DisplayName("2.7 desconexão durante o lote: reload antes do save encontra ativo=false e nada é persistido")
    void desconexaoDuranteOLoteNaoERessuscitada() {
        IntegracaoExterna ativa = integracao(atletaId, tenantId, null);
        IntegracaoExterna desconectada = integracao(atletaId, tenantId, null);
        desconectada.setAtivo(false);
        when(integracaoExternaRepository.findAllActiveByPlataforma(FonteDados.INTERVALS_ICU)).thenReturn(List.of(ativa));
        when(integracaoExternaRepository.findByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.INTERVALS_ICU, tenantId))
                .thenReturn(Optional.of(ativa), Optional.of(desconectada));
        listagemDevolve(A1);

        scheduler.runDailyIncrementalSync();

        verify(ingestionService).importarAtividade(atletaId, "i1", tenantId);
        verify(integracaoExternaRepository, never()).save(any());
    }

    // ----------------------------------------------------------------- 2.8 — contador por delta

    @Test
    @DisplayName("2.8 syncActivityCount mede o delta de TreinoRealizado, não as chamadas bem-sucedidas")
    void contadorMedeODelta() {
        IntegracaoExterna i = atletaAtivo(null);
        i.setSyncActivityCount(5);
        listagemDevolve(A2, A1);
        // o serviço é idempotente: as duas chamadas "dão certo", mas só uma criou linha
        when(treinoRealizadoRepository.countByTenantIdAndAtletaIdAndFonteDados(tenantId, atletaId, FonteDados.INTERVALS_ICU))
                .thenReturn(10L, 11L);

        scheduler.runDailyIncrementalSync();

        assertThat(idsImportadosEmOrdem()).hasSize(2);
        assertThat(salva().getSyncActivityCount()).isEqualTo(6);
    }

    // ----------------------------------------------------------------- 2.9 — CA8

    @Test
    @DisplayName("2.9 multi-tenancy: cada importarAtividade roda com o TenantContext do próprio atleta, limpo ao final")
    void tenantContextPorAtleta() {
        UUID tenantB = UUID.randomUUID();
        UUID atletaB = UUID.randomUUID();
        IntegracaoExterna a = integracao(atletaId, tenantId, null);
        IntegracaoExterna b = integracao(atletaB, tenantB, null);
        b.setExternalAthleteId("i999");
        when(integracaoExternaRepository.findAllActiveByPlataforma(FonteDados.INTERVALS_ICU)).thenReturn(List.of(a, b));
        when(integracaoExternaRepository.findByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.INTERVALS_ICU, tenantId))
                .thenReturn(Optional.of(a));
        when(integracaoExternaRepository.findByAtletaIdAndPlataformaAndTenantId(atletaB, FonteDados.INTERVALS_ICU, tenantB))
                .thenReturn(Optional.of(b));
        when(intervalsIcuClient.listarAtividades(eq(TOKEN), eq(EXTERNAL_ATHLETE), any(), any())).thenReturn(List.of(A1));
        when(intervalsIcuClient.listarAtividades(eq(TOKEN), eq("i999"), any(), any())).thenReturn(List.of(A2));
        List<UUID> tenantsVistos = new ArrayList<>();
        doAnswer(inv -> { tenantsVistos.add(TenantContext.getTenantId()); return null; })
                .when(ingestionService).importarAtividade(any(), anyString(), any());

        scheduler.runDailyIncrementalSync();

        assertThat(tenantsVistos).containsExactly(tenantId, tenantB);
        assertThat(TenantContext.hasTenant()).isFalse();
    }

    // ----------------------------------------------------------------- 2.10 — CA2

    @Test
    @DisplayName("2.10 idempotência: atividade já importada nem chega a importarAtividade — o scheduler não contorna o dedup")
    void jaImportadaNaoEReimportada() {
        atletaAtivo(null);
        listagemDevolve(A1);
        when(treinoRealizadoRepository.findByTenantIdAndFonteDadosAndExternalId(tenantId, FonteDados.INTERVALS_ICU, "i1"))
                .thenReturn(Optional.of(new TreinoRealizado()));

        scheduler.runDailyIncrementalSync();

        verify(ingestionService, never()).importarAtividade(any(), anyString(), any());
        assertThat(salva().getUltimaSincronizacao()).isAfterOrEqualTo(inicioDoTeste);
        assertThat(salva().getLastSyncError()).isNull();
    }
}
