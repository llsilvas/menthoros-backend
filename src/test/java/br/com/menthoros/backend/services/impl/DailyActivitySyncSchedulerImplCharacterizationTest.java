package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.MatchingCandidate;
import br.com.menthoros.backend.dto.MatchingDecision;
import br.com.menthoros.backend.dto.MatchingScoreResult;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.ReconciliationStatus;
import br.com.menthoros.backend.enums.StatusSincronizacao;
import br.com.menthoros.backend.enums.TreinoExecucaoStatus;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.TreinoPlanejadoRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.repository.TreinoReconciliacaoRepository;
import br.com.menthoros.backend.services.ActivityTypeCompatibilityMatrix;
import br.com.menthoros.backend.services.MatchingDecisionEngine;
import br.com.menthoros.backend.services.MatchingScoreCalculator;
import br.com.menthoros.backend.services.helper.CandidateSelector;
import br.com.menthoros.backend.services.helper.ReconciliationDecisionExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Fixa o comportamento ATUAL de {@link DailyActivitySyncSchedulerImpl} (task 3.1, design.md D4).
 * Após a extração (task 3.2), o scheduler delega para {@link CandidateSelector}/
 * {@link ReconciliationDecisionExecutor} — este teste usa instâncias REAIS desses dois
 * colaboradores (construídas sobre os mesmos mocks de repositório/serviço) em vez de mocká-los,
 * para provar equivalência ponta a ponta com o comportamento pré-extração: nenhuma asserção
 * abaixo mudou.
 */
@ExtendWith(MockitoExtension.class)
class DailyActivitySyncSchedulerImplCharacterizationTest {

    @Mock private AtletaRepository atletaRepository;
    @Mock private TreinoRealizadoRepository treinoRealizadoRepository;
    @Mock private TreinoPlanejadoRepository treinoPlanejadoRepository;
    @Mock private TreinoReconciliacaoRepository treinoReconciliacaoRepository;
    @Mock private MatchingScoreCalculator matchingScoreCalculator;
    @Mock private MatchingDecisionEngine matchingDecisionEngine;
    @Mock private ActivityTypeCompatibilityMatrix activityTypeCompatibilityMatrix;

    private DailyActivitySyncSchedulerImpl scheduler;

    @BeforeEach
    void setUpScheduler() {
        CandidateSelector candidateSelector = new CandidateSelector(treinoPlanejadoRepository, activityTypeCompatibilityMatrix);
        ReconciliationDecisionExecutor executor = new ReconciliationDecisionExecutor(
                matchingScoreCalculator, matchingDecisionEngine, treinoRealizadoRepository,
                treinoPlanejadoRepository, treinoReconciliacaoRepository);
        scheduler = new DailyActivitySyncSchedulerImpl(atletaRepository, treinoRealizadoRepository, candidateSelector, executor);
    }

    @Test
    @DisplayName("seleciona atletas via findAllWithStravaConnected e busca pendentes de D-1 filtrando por statusSincronizacao=PENDENTE")
    void selecionaAtletasEBuscaPendentesDeOntem() {
        Atleta atleta = atleta();
        when(atletaRepository.findAllWithStravaConnected()).thenReturn(List.of(atleta));
        when(treinoRealizadoRepository.findByAtletaIdAndDataTreinoAndStatusSincronizacao(
                eq(atleta.getId()), eq(LocalDate.now().minusDays(1)), eq(StatusSincronizacao.PENDENTE)))
                .thenReturn(List.of());

        scheduler.executeDailySync();

        verify(treinoRealizadoRepository).findByAtletaIdAndDataTreinoAndStatusSincronizacao(
                atleta.getId(), LocalDate.now().minusDays(1), StatusSincronizacao.PENDENTE);
    }

    @Test
    @DisplayName("busca candidatos na janela [D-2, D] a partir de D-1 (yesterday - 1, yesterday + 1)")
    void buscaCandidatosNaJanelaDMenos1MaisMenos1() {
        Atleta atleta = atleta();
        TreinoRealizado activity = activity(atleta, LocalDate.now().minusDays(1));
        when(atletaRepository.findAllWithStravaConnected()).thenReturn(List.of(atleta));
        when(treinoRealizadoRepository.findByAtletaIdAndDataTreinoAndStatusSincronizacao(any(), any(), any()))
                .thenReturn(List.of(activity));
        when(treinoPlanejadoRepository.findByAtletaIdAndDataBetween(any(), any(), any()))
                .thenReturn(List.of());
        when(matchingDecisionEngine.decide(eq(activity), anyList()))
                .thenReturn(decisao(ReconciliationStatus.NAO_PLANEJADO, null, "NO_CANDIDATES"));

        scheduler.executeDailySync();

        LocalDate yesterday = LocalDate.now().minusDays(1);
        verify(treinoPlanejadoRepository).findByAtletaIdAndDataBetween(
                atleta.getId(), yesterday.minusDays(1), yesterday.plusDays(1));
    }

    @Test
    @DisplayName("filtra candidatos por compatibilidade de tipo ANTES de calcular score")
    void filtraCandidatosPorCompatibilidadeAntesDeScore() {
        Atleta atleta = atleta();
        TreinoRealizado activity = activity(atleta, LocalDate.now().minusDays(1));
        TreinoPlanejado compativel = planejado(atleta);
        TreinoPlanejado incompativel = planejado(atleta);
        compativel.setTipoTreino(br.com.menthoros.backend.enums.TipoTreino.FACIL);
        incompativel.setTipoTreino(br.com.menthoros.backend.enums.TipoTreino.LONGO);

        when(atletaRepository.findAllWithStravaConnected()).thenReturn(List.of(atleta));
        when(treinoRealizadoRepository.findByAtletaIdAndDataTreinoAndStatusSincronizacao(any(), any(), any()))
                .thenReturn(List.of(activity));
        when(treinoPlanejadoRepository.findByAtletaIdAndDataBetween(any(), any(), any()))
                .thenReturn(List.of(compativel, incompativel));
        when(activityTypeCompatibilityMatrix.isCompatible(activity.getTipoTreino(), compativel.getTipoTreino()))
                .thenReturn(true);
        when(activityTypeCompatibilityMatrix.isCompatible(activity.getTipoTreino(), incompativel.getTipoTreino()))
                .thenReturn(false);
        when(matchingScoreCalculator.calculate(eq(activity), eq(compativel), eq(atleta)))
                .thenReturn(new MatchingScoreResult(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE));
        when(matchingDecisionEngine.decide(eq(activity), anyList()))
                .thenReturn(decisao(ReconciliationStatus.VINCULADO_AUTOMATICO, compativel, "AUTO_MATCH"));

        scheduler.executeDailySync();

        verify(matchingScoreCalculator, never()).calculate(any(), eq(incompativel), any());
        verify(matchingScoreCalculator, times(1)).calculate(eq(activity), eq(compativel), eq(atleta));
    }

    @Test
    @DisplayName("VINCULADO_AUTOMATICO: muta o planejado E salva EXPLICITAMENTE (achado pre-mortem #7 corrigido pela extração — task 3.2)")
    void vinculadoAutomaticoSalvaPlanejadoExplicitamenteAposExtracao() {
        Atleta atleta = atleta();
        TreinoRealizado activity = activity(atleta, LocalDate.now().minusDays(1));
        TreinoPlanejado planejado = planejado(atleta);

        when(atletaRepository.findAllWithStravaConnected()).thenReturn(List.of(atleta));
        when(treinoRealizadoRepository.findByAtletaIdAndDataTreinoAndStatusSincronizacao(any(), any(), any()))
                .thenReturn(List.of(activity));
        when(treinoPlanejadoRepository.findByAtletaIdAndDataBetween(any(), any(), any()))
                .thenReturn(List.of(planejado));
        when(activityTypeCompatibilityMatrix.isCompatible(any(), any())).thenReturn(true);
        when(matchingScoreCalculator.calculate(any(), any(), any()))
                .thenReturn(new MatchingScoreResult(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE));
        when(matchingDecisionEngine.decide(eq(activity), anyList()))
                .thenReturn(decisao(ReconciliationStatus.VINCULADO_AUTOMATICO, planejado, "AUTO_MATCH"));

        scheduler.executeDailySync();

        assertThat(planejado.getStatusTreino()).isEqualTo(TreinoExecucaoStatus.REALIZADO);
        assertThat(planejado.getStatusSincronizacao()).isEqualTo(StatusSincronizacao.SINCRONIZADO);
        assertThat(activity.getTreinoPlanejado()).isSameAs(planejado);
        // achado pre-mortem #7: a extração (ReconciliationDecisionExecutor) agora salva
        // explicitamente o planejado vinculado, em vez de contar com entidade gerenciada implícita
        verify(treinoPlanejadoRepository).save(planejado);
        verify(treinoRealizadoRepository).save(activity);
        verify(treinoReconciliacaoRepository).save(any());
    }

    @Test
    @DisplayName("AMBIGUO: registra estado e auditoria, sem mexer no planejado")
    void ambiguoRegistraSemMexerNoPlanejado() {
        Atleta atleta = atleta();
        TreinoRealizado activity = activity(atleta, LocalDate.now().minusDays(1));
        TreinoPlanejado planejado = planejado(atleta);

        when(atletaRepository.findAllWithStravaConnected()).thenReturn(List.of(atleta));
        when(treinoRealizadoRepository.findByAtletaIdAndDataTreinoAndStatusSincronizacao(any(), any(), any()))
                .thenReturn(List.of(activity));
        when(treinoPlanejadoRepository.findByAtletaIdAndDataBetween(any(), any(), any()))
                .thenReturn(List.of(planejado));
        when(activityTypeCompatibilityMatrix.isCompatible(any(), any())).thenReturn(true);
        when(matchingScoreCalculator.calculate(any(), any(), any()))
                .thenReturn(new MatchingScoreResult(new BigDecimal("0.60"), BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE));
        when(matchingDecisionEngine.decide(eq(activity), anyList()))
                .thenReturn(decisao(ReconciliationStatus.AMBIGUO, planejado, "AMBIGUOUS"));

        scheduler.executeDailySync();

        assertThat(activity.getReconciliationStatus()).isEqualTo(ReconciliationStatus.AMBIGUO);
        assertThat(planejado.getStatusTreino()).isEqualTo(TreinoExecucaoStatus.PENDENTE);
        verify(treinoPlanejadoRepository, never()).save(any());
    }

    @Test
    @DisplayName("NAO_PLANEJADO (sem candidatos na janela): decide via matchingDecisionEngine com lista vazia")
    void semCandidatosDecideComListaVazia() {
        Atleta atleta = atleta();
        TreinoRealizado activity = activity(atleta, LocalDate.now().minusDays(1));

        when(atletaRepository.findAllWithStravaConnected()).thenReturn(List.of(atleta));
        when(treinoRealizadoRepository.findByAtletaIdAndDataTreinoAndStatusSincronizacao(any(), any(), any()))
                .thenReturn(List.of(activity));
        when(treinoPlanejadoRepository.findByAtletaIdAndDataBetween(any(), any(), any()))
                .thenReturn(List.of());
        when(matchingDecisionEngine.decide(eq(activity), eq(List.of())))
                .thenReturn(decisao(ReconciliationStatus.NAO_PLANEJADO, null, "NO_CANDIDATES"));

        scheduler.executeDailySync();

        assertThat(activity.getReconciliationStatus()).isEqualTo(ReconciliationStatus.NAO_PLANEJADO);
        verify(matchingScoreCalculator, never()).calculate(any(), any(), any());
    }

    @Test
    @DisplayName("candidato de outro tenant é filtrado antes do cálculo de score (segurança multi-tenant)")
    void candidatoDeOutroTenantEhFiltrado() {
        Atleta atleta = atleta();
        TreinoRealizado activity = activity(atleta, LocalDate.now().minusDays(1));
        TreinoPlanejado candidatoOutroTenant = planejadoDeOutroTenant();

        when(atletaRepository.findAllWithStravaConnected()).thenReturn(List.of(atleta));
        when(treinoRealizadoRepository.findByAtletaIdAndDataTreinoAndStatusSincronizacao(any(), any(), any()))
                .thenReturn(List.of(activity));
        when(treinoPlanejadoRepository.findByAtletaIdAndDataBetween(any(), any(), any()))
                .thenReturn(List.of(candidatoOutroTenant));
        when(activityTypeCompatibilityMatrix.isCompatible(any(), any())).thenReturn(true);
        when(matchingDecisionEngine.decide(eq(activity), eq(List.of())))
                .thenReturn(decisao(ReconciliationStatus.NAO_PLANEJADO, null, "NO_CANDIDATES"));

        scheduler.executeDailySync();

        verify(matchingScoreCalculator, never()).calculate(any(), eq(candidatoOutroTenant), any());
    }

    @Test
    @DisplayName("atividade de outro tenant (mismatch atleta x atividade) é pulada sem processar")
    void atividadeDeOutroTenantEhPulada() {
        Atleta atleta = atleta();
        TreinoRealizado activityOutroTenant = activityDeOutroTenant(atleta.getId());

        when(atletaRepository.findAllWithStravaConnected()).thenReturn(List.of(atleta));
        when(treinoRealizadoRepository.findByAtletaIdAndDataTreinoAndStatusSincronizacao(any(), any(), any()))
                .thenReturn(List.of(activityOutroTenant));

        scheduler.executeDailySync();

        verify(treinoPlanejadoRepository, never()).findByAtletaIdAndDataBetween(any(), any(), any());
        verify(treinoRealizadoRepository, never()).save(any());
    }

    @Test
    @DisplayName("auditoria TreinoReconciliacao gravada com beforeStatus=PENDENTE e afterStatus=decision.status")
    void auditoriaGravadaComBeforeEAfterStatus() {
        Atleta atleta = atleta();
        TreinoRealizado activity = activity(atleta, LocalDate.now().minusDays(1));

        when(atletaRepository.findAllWithStravaConnected()).thenReturn(List.of(atleta));
        when(treinoRealizadoRepository.findByAtletaIdAndDataTreinoAndStatusSincronizacao(any(), any(), any()))
                .thenReturn(List.of(activity));
        when(treinoPlanejadoRepository.findByAtletaIdAndDataBetween(any(), any(), any()))
                .thenReturn(List.of());
        when(matchingDecisionEngine.decide(eq(activity), eq(List.of())))
                .thenReturn(decisao(ReconciliationStatus.NAO_PLANEJADO, null, "NO_CANDIDATES"));

        scheduler.executeDailySync();

        ArgumentCaptor<br.com.menthoros.backend.entity.TreinoReconciliacao> captor =
                ArgumentCaptor.forClass(br.com.menthoros.backend.entity.TreinoReconciliacao.class);
        verify(treinoReconciliacaoRepository).save(captor.capture());
        assertThat(captor.getValue().getBeforeStatus()).isEqualTo(ReconciliationStatus.PENDENTE);
        assertThat(captor.getValue().getAfterStatus()).isEqualTo(ReconciliationStatus.NAO_PLANEJADO);
    }

    // ---- helpers ----

    private Atleta atleta() {
        Assessoria assessoria = new Assessoria();
        assessoria.setId(UUID.randomUUID());
        Atleta atleta = new Atleta();
        atleta.setId(UUID.randomUUID());
        atleta.setAssessoria(assessoria);
        return atleta;
    }

    private TreinoRealizado activity(Atleta atleta, LocalDate data) {
        TreinoRealizado t = new TreinoRealizado();
        t.setId(UUID.randomUUID());
        t.setAtleta(atleta);
        t.setDataTreino(data);
        t.setDuracaoMin(Duration.ofMinutes(30));
        t.setDistanciaKm(java.math.BigDecimal.valueOf(5));
        return t;
    }

    private TreinoRealizado activityDeOutroTenant(UUID atletaEsperadoId) {
        Assessoria outraAssessoria = new Assessoria();
        outraAssessoria.setId(UUID.randomUUID());
        Atleta atletaDaActivity = new Atleta();
        atletaDaActivity.setId(atletaEsperadoId);
        atletaDaActivity.setAssessoria(outraAssessoria);

        TreinoRealizado t = new TreinoRealizado();
        t.setId(UUID.randomUUID());
        t.setAtleta(atletaDaActivity);
        t.setDataTreino(LocalDate.now().minusDays(1));
        return t;
    }

    private TreinoPlanejado planejado(Atleta atleta) {
        TreinoPlanejado p = new TreinoPlanejado();
        p.setId(UUID.randomUUID());
        p.setAtleta(atleta);
        p.setDataTreino(LocalDate.now().minusDays(1));
        p.setDuracaoMin(Duration.ofMinutes(30));
        p.setDistanciaKm(java.math.BigDecimal.valueOf(5));
        return p;
    }

    private TreinoPlanejado planejadoDeOutroTenant() {
        Assessoria outraAssessoria = new Assessoria();
        outraAssessoria.setId(UUID.randomUUID());
        Atleta outroAtleta = new Atleta();
        outroAtleta.setId(UUID.randomUUID());
        outroAtleta.setAssessoria(outraAssessoria);

        TreinoPlanejado p = new TreinoPlanejado();
        p.setId(UUID.randomUUID());
        p.setAtleta(outroAtleta);
        p.setDataTreino(LocalDate.now().minusDays(1));
        return p;
    }

    private MatchingDecision decisao(ReconciliationStatus status, TreinoPlanejado selected, String reasonCode) {
        List<MatchingCandidate> ranked = selected != null
                ? List.of(new MatchingCandidate(selected, new MatchingScoreResult(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE), 1))
                : List.of();
        return new MatchingDecision(status, selected, ranked, reasonCode, "razao: " + reasonCode);
    }
}
