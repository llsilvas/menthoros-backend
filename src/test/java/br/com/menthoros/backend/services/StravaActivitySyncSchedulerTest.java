package br.com.menthoros.backend.services;

import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.enums.ErroCategoriaPull;
import br.com.menthoros.backend.enums.ResultadoPull;
import br.com.menthoros.backend.repository.IntegracaoExternaRepository;
import br.com.menthoros.backend.services.helper.PullResultado;
import br.com.menthoros.backend.services.helper.SyncPullLogWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StravaActivitySyncSchedulerTest {

    @Mock
    private IntegracaoExternaRepository integracaoExternaRepository;

    @Mock
    private StravaActivityService stravaActivityService;

    @Mock
    private SyncPullLogWriter pullLogWriter;

    @InjectMocks
    private StravaActivitySyncScheduler scheduler;

    private static final PullResultado COMPLETO_1 = new PullResultado(ResultadoPull.COMPLETO, null, 1, 0);

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldRunDailySyncForAllActiveStravaIntegrations() {
        UUID tenantId = UUID.randomUUID();
        UUID atletaId = UUID.randomUUID();

        IntegracaoExterna integracao = new IntegracaoExterna();
        integracao.setPlataforma(FonteDados.STRAVA);
        integracao.setAtivo(true);
        integracao.setTenantId(tenantId);

        Atleta atleta = new Atleta();
        atleta.setId(atletaId);
        Assessoria assessoria = new Assessoria();
        assessoria.setId(tenantId);
        atleta.setAssessoria(assessoria);
        integracao.setAtleta(atleta);

        when(integracaoExternaRepository.findAllActiveByPlataforma(FonteDados.STRAVA))
                .thenReturn(List.of(integracao));
        when(integracaoExternaRepository.findByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.STRAVA, tenantId))
                .thenReturn(Optional.of(integracao));
        when(stravaActivityService.pullAgendado(any(UUID.class))).thenReturn(COMPLETO_1);

        scheduler.runDailyIncrementalSync();

        ArgumentCaptor<UUID> atletaIdCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(stravaActivityService, times(1)).pullAgendado(atletaIdCaptor.capture());
        assertEquals(atletaId, atletaIdCaptor.getValue());
        assertFalse(TenantContext.hasTenant());
    }

    @Test
    @org.junit.jupiter.api.DisplayName("late-check: atleta pausado ENTRE a listagem e o sync é pulado no mesmo ciclo (TOCTOU, D5.2)")
    void shouldSkipAthletePausedBetweenListingAndSync() {
        UUID tenantId = UUID.randomUUID();
        UUID atletaId = UUID.randomUUID();

        IntegracaoExterna integracaoNaListagem = new IntegracaoExterna();
        integracaoNaListagem.setPlataforma(FonteDados.STRAVA);
        integracaoNaListagem.setAtivo(true);
        integracaoNaListagem.setTenantId(tenantId);
        integracaoNaListagem.setAutoSyncPausado(false);

        Atleta atleta = new Atleta();
        atleta.setId(atletaId);
        Assessoria assessoria = new Assessoria();
        assessoria.setId(tenantId);
        atleta.setAssessoria(assessoria);
        integracaoNaListagem.setAtleta(atleta);

        // revalidação fresca (chamada logo antes do sync) já reflete autoSyncPausado=true —
        // simula o coach pausando o atleta ENTRE a listagem inicial e o processamento dele
        IntegracaoExterna integracaoRevalidada = new IntegracaoExterna();
        integracaoRevalidada.setPlataforma(FonteDados.STRAVA);
        integracaoRevalidada.setAtivo(true);
        integracaoRevalidada.setAutoSyncPausado(true);

        when(integracaoExternaRepository.findAllActiveByPlataforma(FonteDados.STRAVA))
                .thenReturn(List.of(integracaoNaListagem));
        when(integracaoExternaRepository.findByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.STRAVA, tenantId))
                .thenReturn(Optional.of(integracaoRevalidada));

        scheduler.runDailyIncrementalSync();

        verify(stravaActivityService, never()).pullAgendado(any(UUID.class));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("use-best-effort-for-threshold-inference, tasks.md 3.4: TenantContext "
            + "binding correto tenant->atleta no laço (não só ordenação/não-nulidade) — achado da 2ª "
            + "rodada de pre-mortem: um teste que só verifica \"setado antes da chamada\" passaria mesmo "
            + "se o scheduler vazasse o tenant do atleta anterior numa iteração seguinte")
    void tenantContextBindingCorretoPorAtletaNoLaco() {
        UUID tenantA = UUID.randomUUID();
        UUID atletaA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        UUID atletaB = UUID.randomUUID();

        IntegracaoExterna integracaoA = integracaoAtiva(tenantA, atletaA);
        IntegracaoExterna integracaoB = integracaoAtiva(tenantB, atletaB);

        when(integracaoExternaRepository.findAllActiveByPlataforma(FonteDados.STRAVA))
                .thenReturn(List.of(integracaoA, integracaoB));
        when(integracaoExternaRepository.findByAtletaIdAndPlataformaAndTenantId(atletaA, FonteDados.STRAVA, tenantA))
                .thenReturn(Optional.of(integracaoA));
        when(integracaoExternaRepository.findByAtletaIdAndPlataformaAndTenantId(atletaB, FonteDados.STRAVA, tenantB))
                .thenReturn(Optional.of(integracaoB));

        // Captura o tenant EFETIVAMENTE lido de TenantContext no momento em que a chamada externa
        // acontece pra cada atleta — não o valor esperado, o valor real visto pelo colaborador.
        java.util.Map<UUID, UUID> tenantVistoPorAtleta = new java.util.HashMap<>();
        when(stravaActivityService.pullAgendado(any(UUID.class))).thenAnswer(invocation -> {
            UUID atletaIdChamado = invocation.getArgument(0);
            tenantVistoPorAtleta.put(atletaIdChamado, TenantContext.getRequiredTenantId());
            return COMPLETO_1;
        });

        scheduler.runDailyIncrementalSync();

        assertEquals(tenantA, tenantVistoPorAtleta.get(atletaA),
                "tenant visto durante o processamento do atleta A deve ser o tenant A, não vazado de outra iteração");
        assertEquals(tenantB, tenantVistoPorAtleta.get(atletaB),
                "tenant visto durante o processamento do atleta B deve ser o tenant B, não o tenant A residual");
        assertFalse(TenantContext.hasTenant(), "TenantContext deve estar limpo após o ciclo inteiro");
    }

    @Test
    @org.junit.jupiter.api.DisplayName("CA8 — cada ciclo grava o registro do pull com o tenant da integração")
    void registraOPull() {
        UUID tenantId = UUID.randomUUID();
        UUID atletaId = UUID.randomUUID();
        IntegracaoExterna integracao = integracaoAtiva(tenantId, atletaId);
        when(integracaoExternaRepository.findAllActiveByPlataforma(FonteDados.STRAVA)).thenReturn(List.of(integracao));
        when(integracaoExternaRepository.findByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.STRAVA, tenantId))
                .thenReturn(Optional.of(integracao));
        PullResultado parcial = new PullResultado(ResultadoPull.PARCIAL, ErroCategoriaPull.RATE_LIMIT, 3, 0);
        when(stravaActivityService.pullAgendado(atletaId)).thenReturn(parcial);

        scheduler.runDailyIncrementalSync();

        verify(pullLogWriter).registrar(org.mockito.ArgumentMatchers.eq(tenantId), org.mockito.ArgumentMatchers.eq(atletaId),
                org.mockito.ArgumentMatchers.eq(FonteDados.STRAVA), org.mockito.ArgumentMatchers.eq(parcial), any());
    }

    @Test
    @org.junit.jupiter.api.DisplayName("CA8 — exceção fora do pull (rede para bug) registra FALHA/INESPERADO com 0")
    void excecaoRegistraFalha() {
        UUID tenantId = UUID.randomUUID();
        UUID atletaId = UUID.randomUUID();
        IntegracaoExterna integracao = integracaoAtiva(tenantId, atletaId);
        when(integracaoExternaRepository.findAllActiveByPlataforma(FonteDados.STRAVA)).thenReturn(List.of(integracao));
        when(integracaoExternaRepository.findByAtletaIdAndPlataformaAndTenantId(atletaId, FonteDados.STRAVA, tenantId))
                .thenReturn(Optional.of(integracao));
        when(stravaActivityService.pullAgendado(atletaId)).thenThrow(new IllegalStateException("Atleta sem integração Strava ativa"));

        scheduler.runDailyIncrementalSync();

        verify(pullLogWriter).registrar(org.mockito.ArgumentMatchers.eq(tenantId), org.mockito.ArgumentMatchers.eq(atletaId),
                org.mockito.ArgumentMatchers.eq(FonteDados.STRAVA),
                org.mockito.ArgumentMatchers.eq(new PullResultado(ResultadoPull.FALHA, ErroCategoriaPull.INESPERADO, 0, 0)), any());
        assertFalse(TenantContext.hasTenant());
    }

    private static IntegracaoExterna integracaoAtiva(UUID tenantId, UUID atletaId) {
        IntegracaoExterna integracao = new IntegracaoExterna();
        integracao.setPlataforma(FonteDados.STRAVA);
        integracao.setAtivo(true);
        integracao.setTenantId(tenantId);
        integracao.setAutoSyncPausado(false);

        Atleta atleta = new Atleta();
        atleta.setId(atletaId);
        Assessoria assessoria = new Assessoria();
        assessoria.setId(tenantId);
        atleta.setAssessoria(assessoria);
        integracao.setAtleta(atleta);
        return integracao;
    }
}
