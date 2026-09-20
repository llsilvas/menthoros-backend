package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.output.MelhorEsforcoDto;
import br.com.menthoros.backend.enums.FonteLimiarInferencia;
import br.com.menthoros.backend.exception.IntervalsIcuApiException;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.MetricasDiariasRepository;
import br.com.menthoros.backend.repository.PlanoMetadadosRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.repository.projection.LimiarPaceStatusProjection;
import br.com.menthoros.backend.services.MelhorEsforcoService;
import br.com.menthoros.backend.services.PlanoMetadadosService;
import br.com.menthoros.backend.services.helper.AthleteThresholdUpdater;
import br.com.menthoros.backend.services.helper.PaceLimiarResolvido;
import br.com.menthoros.backend.services.helper.ThresholdInferenceService;
import br.com.menthoros.backend.services.helper.TsbRecalculoExecutor;
import br.com.menthoros.backend.testsupport.LimiarPaceStatusProjectionTestStub;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Testes de orquestração de {@code TsbServiceImpl} pós-refactor
 * (refactor-threshold-call-outside-transaction, seção 4) — cobrem especificamente o que a
 * restruturação de fronteira transacional promete, não a matemática de CTL/ATL/TSB (essa
 * permanece coberta pelos testes de {@code TsbDiaPersister} e pelos arquivos de semântica
 * existentes, que continuam verdes via o mesmo comportamento observável).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TsbServiceImpl — orquestração pós-split (design.md D2)")
class TsbServiceImplOrquestracaoTest {

    @Mock private TreinoRealizadoRepository treinoRealizadoRepository;
    @Mock private PlanoMetadadosRepository planoMetaDadosRepository;
    @Mock private MetricasDiariasRepository metricasDiariasRepository;
    @Mock private AtletaRepository atletaRepository;
    @Mock private MetricasAlertaService metricasAlertaService;
    @Mock private AthleteThresholdUpdater athleteThresholdUpdater;
    @Mock private ThresholdInferenceService thresholdInferenceService;
    @Mock private TsbRecalculoExecutor tsbRecalculoExecutor;
    @Mock private PlanoMetadadosService planoMetadadosService;
    @Mock private TsbDiaPersister tsbDiaPersister;
    @Mock private MelhorEsforcoService melhorEsforcoService;

    private TsbServiceImpl service;

    private static final UUID ATLETA_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final LocalDate HOJE = LocalDate.of(2026, 6, 22);

    private void construirService() {
        service = new TsbServiceImpl(treinoRealizadoRepository, planoMetaDadosRepository,
                metricasDiariasRepository, atletaRepository, metricasAlertaService,
                athleteThresholdUpdater, thresholdInferenceService, tsbRecalculoExecutor,
                planoMetadadosService, tsbDiaPersister, melhorEsforcoService);
    }

    // =========================================================================
    // 4.1 — resolverPaceSeNecessario (testado via atualizarTsbDia, único caller externo)
    // =========================================================================

    @Nested
    @DisplayName("resolverPaceSeNecessario (via atualizarTsbDia)")
    class ResolverPaceSeNecessario {

        @Test
        @DisplayName("pace não desatualizado: não busca treinos, passa null pro persister")
        void paceNaoDesatualizado_naoBuscaTreinos() {
            construirService();
            LimiarPaceStatusProjection status = LimiarPaceStatusProjectionTestStub.naoDesatualizado(TENANT_ID, HOJE);
            when(atletaRepository.findLimiarPaceStatusById(ATLETA_ID)).thenReturn(Optional.of(status));
            when(thresholdInferenceService.isPaceLimiarDesatualizado(any(), any(), eq(HOJE))).thenReturn(false);

            service.atualizarTsbDia(ATLETA_ID, HOJE);

            verify(treinoRealizadoRepository, never())
                    .findByAtletaIdAndTenantIdAndDataTreinoBetween(any(), any(), any(), any());
            verify(tsbDiaPersister).atualizarDiaTransacional(ATLETA_ID, HOJE, true, null);
        }

        @Test
        @DisplayName("atleta sem assessoria/inexistente: retorna sem buscar nada, passa null pro persister")
        void atletaSemAssessoria_naoAltera() {
            construirService();
            when(atletaRepository.findLimiarPaceStatusById(ATLETA_ID)).thenReturn(Optional.empty());

            service.atualizarTsbDia(ATLETA_ID, HOJE);

            verifyNoInteractions(treinoRealizadoRepository, thresholdInferenceService, athleteThresholdUpdater,
                    melhorEsforcoService);
            verify(tsbDiaPersister).atualizarDiaTransacional(ATLETA_ID, HOJE, true, null);
        }

        @Test
        @DisplayName("caminho feliz: pace desatualizado resolve a fonte e passa o resultado pro persister")
        void paceDesatualizado_resolveFontePace() {
            construirService();
            LimiarPaceStatusProjection status = LimiarPaceStatusProjectionTestStub
                    .projection(TENANT_ID, new BigDecimal("5.0000"), HOJE.minusDays(91));
            when(atletaRepository.findLimiarPaceStatusById(ATLETA_ID)).thenReturn(Optional.of(status));
            when(thresholdInferenceService.isPaceLimiarDesatualizado(
                    eq(new BigDecimal("5.0000")), eq(HOJE.minusDays(91)), eq(HOJE))).thenReturn(true);
            when(athleteThresholdUpdater.buscarTreinos30d(eq(ATLETA_ID), eq(TENANT_ID), eq(HOJE)))
                    .thenReturn(List.of());
            when(planoMetaDadosRepository.findPaceLimiarEstimadoByAtletaId(ATLETA_ID))
                    .thenReturn(Optional.of(new BigDecimal("5.0000")));
            PaceLimiarResolvido resolvido = new PaceLimiarResolvido(
                    FonteLimiarInferencia.MEDIA_TREINOS, new BigDecimal("4.8000"), null);
            when(athleteThresholdUpdater.resolverFontePace(eq(ATLETA_ID), eq(TENANT_ID), eq(HOJE), any(), eq(new BigDecimal("5.0000")), any()))
                    .thenReturn(Optional.of(resolvido));

            service.atualizarTsbDia(ATLETA_ID, HOJE);

            verify(tsbDiaPersister).atualizarDiaTransacional(ATLETA_ID, HOJE, true, resolvido);
        }

        @Test
        @DisplayName("use-best-effort: melhor esforço buscado é repassado pra resolverFontePace")
        void paceDesatualizado_repassaMelhorEsforcoParaResolverFontePace() {
            construirService();
            LimiarPaceStatusProjection status = LimiarPaceStatusProjectionTestStub
                    .projection(TENANT_ID, new BigDecimal("5.0000"), HOJE.minusDays(91));
            MelhorEsforcoDto dez = new MelhorEsforcoDto("10k", 10000.0, 2500, "pace");
            when(atletaRepository.findLimiarPaceStatusById(ATLETA_ID)).thenReturn(Optional.of(status));
            when(thresholdInferenceService.isPaceLimiarDesatualizado(
                    eq(new BigDecimal("5.0000")), eq(HOJE.minusDays(91)), eq(HOJE))).thenReturn(true);
            when(athleteThresholdUpdater.buscarTreinos30d(eq(ATLETA_ID), eq(TENANT_ID), eq(HOJE)))
                    .thenReturn(List.of());
            when(planoMetaDadosRepository.findPaceLimiarEstimadoByAtletaId(ATLETA_ID))
                    .thenReturn(Optional.of(new BigDecimal("5.0000")));
            when(melhorEsforcoService.buscar(ATLETA_ID, "42d")).thenReturn(List.of(dez));
            when(athleteThresholdUpdater.resolverFontePace(any(), any(), any(), any(), any(), eq(List.of(dez))))
                    .thenReturn(Optional.empty());

            service.atualizarTsbDia(ATLETA_ID, HOJE);

            verify(athleteThresholdUpdater).resolverFontePace(
                    eq(ATLETA_ID), eq(TENANT_ID), eq(HOJE), any(), eq(new BigDecimal("5.0000")), eq(List.of(dez)));
        }

        @Test
        @DisplayName("AC4: falha na busca de melhor esforço não impede a persistência do TSB do dia "
                + "(efeito observável, não só ausência de exceção — achado da 3ª rodada de pre-mortem)")
        void falhaNaBuscaDeMelhorEsforco_naoImpedePersistenciaDoTsb() {
            construirService();
            LimiarPaceStatusProjection status = LimiarPaceStatusProjectionTestStub
                    .projection(TENANT_ID, new BigDecimal("5.0000"), HOJE.minusDays(91));
            when(atletaRepository.findLimiarPaceStatusById(ATLETA_ID)).thenReturn(Optional.of(status));
            when(thresholdInferenceService.isPaceLimiarDesatualizado(
                    eq(new BigDecimal("5.0000")), eq(HOJE.minusDays(91)), eq(HOJE))).thenReturn(true);
            when(athleteThresholdUpdater.buscarTreinos30d(eq(ATLETA_ID), eq(TENANT_ID), eq(HOJE)))
                    .thenReturn(List.of());
            when(planoMetaDadosRepository.findPaceLimiarEstimadoByAtletaId(ATLETA_ID))
                    .thenReturn(Optional.of(new BigDecimal("5.0000")));
            when(melhorEsforcoService.buscar(ATLETA_ID, "42d"))
                    .thenThrow(new IntervalsIcuApiException(null, "timeout simulado"));
            when(athleteThresholdUpdater.resolverFontePace(any(), any(), any(), any(), any(), eq(List.of())))
                    .thenReturn(Optional.empty());

            service.atualizarTsbDia(ATLETA_ID, HOJE);

            verify(tsbDiaPersister).atualizarDiaTransacional(eq(ATLETA_ID), eq(HOJE), eq(true), any());
        }
    }

    // =========================================================================
    // 3.2 — buscarMelhorEsforcoSeguro: best-effort, nunca propaga
    // =========================================================================

    @Nested
    @DisplayName("buscarMelhorEsforcoSeguro (design.md D5)")
    class BuscarMelhorEsforcoSeguro {

        @org.junit.jupiter.api.AfterEach
        void limparTenantContext() {
            br.com.menthoros.backend.multitenancy.TenantContext.clear();
        }

        @Test
        @DisplayName("sucesso devolve a lista")
        void sucesso_devolveLista() {
            construirService();
            MelhorEsforcoDto dez = new MelhorEsforcoDto("10k", 10000.0, 2500, "pace");
            when(melhorEsforcoService.buscar(ATLETA_ID, "42d")).thenReturn(List.of(dez));

            List<MelhorEsforcoDto> resultado = service.buscarMelhorEsforcoSeguro(ATLETA_ID, TENANT_ID);

            assertThat(resultado).containsExactly(dez);
        }

        @Test
        @DisplayName("IntervalsIcuApiException devolve lista vazia sem propagar")
        void excecaoDeApi_devolveListaVaziaSemPropagar() {
            construirService();
            when(melhorEsforcoService.buscar(ATLETA_ID, "42d"))
                    .thenThrow(new IntervalsIcuApiException(null, "timeout simulado"));

            List<MelhorEsforcoDto> resultado = service.buscarMelhorEsforcoSeguro(ATLETA_ID, TENANT_ID);

            assertThat(resultado).isEmpty();
        }

        @Test
        @DisplayName("RuntimeException genérica devolve lista vazia sem propagar")
        void runtimeExceptionGenerica_devolveListaVaziaSemPropagar() {
            construirService();
            when(melhorEsforcoService.buscar(ATLETA_ID, "42d"))
                    .thenThrow(new RuntimeException("erro inesperado"));

            List<MelhorEsforcoDto> resultado = service.buscarMelhorEsforcoSeguro(ATLETA_ID, TENANT_ID);

            assertThat(resultado).isEmpty();
        }

        @Test
        @DisplayName("TenantContext setado mas divergente do tenant resolvido: pula a busca sem "
                + "chamar o serviço (achado convergente code-reviewer + security-reviewer, QA) — "
                + "defesa em profundidade além da convenção de set/clear por caller")
        void tenantContextDivergente_pulaABuscaSemChamarOServico() {
            construirService();
            UUID tenantDivergente = UUID.randomUUID();
            br.com.menthoros.backend.multitenancy.TenantContext.setTenantId(tenantDivergente);

            List<MelhorEsforcoDto> resultado = service.buscarMelhorEsforcoSeguro(ATLETA_ID, TENANT_ID);

            assertThat(resultado).isEmpty();
            verify(melhorEsforcoService, never()).buscar(any(), any());
        }

        @Test
        @DisplayName("TenantContext ausente (não setado): segue a chamada normalmente — "
                + "MelhorEsforcoServiceImpl lança IllegalStateException, capturada pelo catch amplo")
        void tenantContextAusente_seguirChamadaQueFalhaComIllegalState() {
            construirService();
            when(melhorEsforcoService.buscar(ATLETA_ID, "42d"))
                    .thenThrow(new IllegalStateException("TenantContext não setado"));

            List<MelhorEsforcoDto> resultado = service.buscarMelhorEsforcoSeguro(ATLETA_ID, TENANT_ID);

            assertThat(resultado).isEmpty();
        }
    }

    // =========================================================================
    // 4.2 — teste estrutural: resolução de pace roda antes de qualquer persistência
    // =========================================================================

    @Test
    @DisplayName("4.2: melhor esforço + resolverFontePace rodam antes de qualquer interação com o "
            + "persister (InOrder) — agora incluindo a chamada ao MelhorEsforcoService")
    void resolucaoDeFonteRodaAntesDaPersistencia() {
        construirService();
        LimiarPaceStatusProjection status = LimiarPaceStatusProjectionTestStub
                .projection(TENANT_ID, new BigDecimal("5.0000"), HOJE.minusDays(91));
        when(atletaRepository.findLimiarPaceStatusById(ATLETA_ID)).thenReturn(Optional.of(status));
        when(thresholdInferenceService.isPaceLimiarDesatualizado(any(), any(), eq(HOJE))).thenReturn(true);
        when(athleteThresholdUpdater.buscarTreinos30d(any(), any(), any())).thenReturn(List.of());
        when(melhorEsforcoService.buscar(any(), any())).thenReturn(List.of());
        when(athleteThresholdUpdater.resolverFontePace(any(), any(), any(), any(), any(), any()))
                .thenReturn(Optional.empty());

        service.atualizarTsbDia(ATLETA_ID, HOJE);

        InOrder ordem = inOrder(melhorEsforcoService, athleteThresholdUpdater, tsbDiaPersister);
        ordem.verify(melhorEsforcoService).buscar(any(), any());
        ordem.verify(athleteThresholdUpdater).resolverFontePace(any(), any(), any(), any(), any(), any());
        ordem.verify(tsbDiaPersister).atualizarDiaTransacional(any(), any(), anyBoolean(), any());
    }

    // =========================================================================
    // 4.3 — recalcularDesde resolve a fonte uma vez, não por dia do intervalo
    // =========================================================================

    @Test
    @DisplayName("4.3: recalcularDesde resolve a fonte de pace 1 vez (hoje=fim), não por dia")
    void recalcularDesde_resolveFonteUmaVezPorIntervalo() {
        construirService();
        // recalcularDesde resolve `fim` via LocalDate.now() quando não há métrica futura
        // materializada (comportamento real, não mockável sem um clock injetado) — o intervalo
        // precisa ser ancorado em "agora", não em HOJE (data fixa usada nos outros testes).
        LocalDate inicio = LocalDate.now().minusDays(4);
        when(metricasDiariasRepository.findDataUltimaMetrica(ATLETA_ID)).thenReturn(null); // fim = hoje (now)
        LimiarPaceStatusProjection status = LimiarPaceStatusProjectionTestStub
                .projection(TENANT_ID, new BigDecimal("5.0000"), LocalDate.now().minusDays(91));
        when(atletaRepository.findLimiarPaceStatusById(ATLETA_ID)).thenReturn(Optional.of(status));
        when(thresholdInferenceService.isPaceLimiarDesatualizado(any(), any(), any())).thenReturn(true);
        when(athleteThresholdUpdater.buscarTreinos30d(any(), any(), any())).thenReturn(List.of());
        when(athleteThresholdUpdater.resolverFontePace(any(), any(), any(), any(), any(), any()))
                .thenReturn(Optional.empty());

        service.recalcularDesde(ATLETA_ID, inicio);

        // paceStale=true, mas resolverFontePace/findLimiarPaceStatusById só rodam 1 vez pro
        // intervalo inteiro (com hoje=fim), não uma vez por dia — mesmo com 5 dias no laço.
        verify(atletaRepository, times(1)).findLimiarPaceStatusById(ATLETA_ID);
        verify(athleteThresholdUpdater, times(1)).resolverFontePace(any(), any(), any(), any(), any(), any());
        verify(tsbDiaPersister, times(5)).atualizarDiaTransacional(eq(ATLETA_ID), any(), anyBoolean(), any());
    }

    // =========================================================================
    // 4.5 — reflexão: sem @Transactional residual nos 2 pontos de entrada
    // =========================================================================

    @Test
    @DisplayName("4.5: atualizarTsbDia(UUID,LocalDate) e recalcularDesde não carregam @Transactional")
    void semTransactionalResidual() throws NoSuchMethodException {
        Method atualizarTsbDia = TsbServiceImpl.class.getMethod("atualizarTsbDia", UUID.class, LocalDate.class);
        Method recalcularDesde = TsbServiceImpl.class.getMethod("recalcularDesde", UUID.class, LocalDate.class);

        for (Method m : List.of(atualizarTsbDia, recalcularDesde)) {
            Annotation[] anotacoes = m.getAnnotations();
            boolean temTransactional = List.of(anotacoes).stream()
                    .anyMatch(a -> a.annotationType().getSimpleName().equals("Transactional"));
            assertThat(temTransactional)
                    .as("%s não deve carregar @Transactional (design.md D2) — a resolução de fonte "
                            + "de pace precisa rodar fora de qualquer transação", m.getName())
                    .isFalse();
        }
    }
}
