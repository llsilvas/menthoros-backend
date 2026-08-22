package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.MatchingDecision;
import br.com.menthoros.backend.dto.intervalsicu.IcuActivityDto;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.ReconciliationStatus;
import br.com.menthoros.backend.exception.DomainConflictException;
import br.com.menthoros.backend.services.IntervalsIcuConnectionService;
import br.com.menthoros.backend.services.TsbService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntervalsIcuActivityPersisterTest {

    @Mock private IntervalsIcuConnectionService intervalsIcuConnectionService;
    @Mock private IntervalsIcuActivityMapper intervalsIcuActivityMapper;
    @Mock private TreinoDedupHelper treinoDedupHelper;
    @Mock private TssCalculatorService tssCalculatorService;
    @Mock private TsbService tsbService;
    @Mock private CandidateSelector candidateSelector;
    @Mock private ReconciliationDecisionExecutor reconciliationDecisionExecutor;
    @Mock private ApplicationEventPublisher eventPublisher;

    private IntervalsIcuActivityPersister persister;
    private io.micrometer.core.instrument.MeterRegistry meterRegistry;

    private UUID tenantId;
    private Atleta atleta;
    private IcuActivityDto dto;
    private static final String EXTERNAL_ID = "i166338796";

    @BeforeEach
    void setUp() {
        meterRegistry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        persister = new IntervalsIcuActivityPersister(intervalsIcuConnectionService, intervalsIcuActivityMapper,
                treinoDedupHelper, tssCalculatorService, tsbService, candidateSelector, reconciliationDecisionExecutor,
                eventPublisher, meterRegistry);

        tenantId = UUID.randomUUID();
        Assessoria assessoria = new Assessoria();
        assessoria.setId(tenantId);
        atleta = new Atleta();
        atleta.setId(UUID.randomUUID());
        atleta.setAssessoria(assessoria);
        dto = new IcuActivityDto(EXTERNAL_ID, "i641775", "Run", "Corrida", "2026-07-16T08:00:00", null,
                1800, 1850, 5000.0, null, null, null, null, null, null, null, null, null, null, null);
    }

    @Nested
    @DisplayName("persistir")
    class Persistir {

        @Test
        @DisplayName("TOCTOU (pre-mortem #9): conexão desativada entre a leitura inicial e este ponto aborta com 409, nada persiste")
        void conexaoDesativadaAbortaComConflito() {
            when(intervalsIcuConnectionService.conexaoAtiva(atleta.getId(), tenantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> persister.persistir(dto, atleta, tenantId, EXTERNAL_ID))
                    .isInstanceOf(DomainConflictException.class);

            verify(treinoDedupHelper, never()).saveIdempotent(any(), any(), any());
        }

        @Test
        @DisplayName("insert novo: calcula TSS, atualiza TSB, reconcilia e publica evento")
        void insertNovoExecutaPosIngestaoCompleta() {
            when(intervalsIcuConnectionService.conexaoAtiva(atleta.getId(), tenantId))
                    .thenReturn(Optional.of(new IntegracaoExterna()));
            TreinoRealizado mapeado = treinoMapeado();
            when(intervalsIcuActivityMapper.map(dto, atleta)).thenReturn(mapeado);
            when(treinoDedupHelper.saveIdempotent(mapeado, EXTERNAL_ID, atleta.getId()))
                    .thenReturn(new TreinoDedupHelper.SaveResult(mapeado, true));
            when(tssCalculatorService.calcularTss(mapeado)).thenReturn(55);
            List<TreinoPlanejado> candidatos = List.of();
            when(candidateSelector.buscarCandidatos(mapeado, tenantId)).thenReturn(candidatos);
            when(reconciliationDecisionExecutor.executar(mapeado, candidatos, atleta))
                    .thenReturn(new MatchingDecision(ReconciliationStatus.NAO_PLANEJADO, null, List.of(), "NO_CANDIDATES", "razão"));

            TreinoRealizado resultado = persister.persistir(dto, atleta, tenantId, EXTERNAL_ID);

            assertThat(resultado).isSameAs(mapeado);
            assertThat(mapeado.getTssCalculado()).isEqualTo(55);
            verify(tsbService).atualizarTsbDia(atleta.getId(), mapeado.getDataTreino());
            verify(reconciliationDecisionExecutor).executar(mapeado, candidatos, atleta);
            verify(eventPublisher).publishEvent(any(br.com.menthoros.backend.events.TreinoRegistradoEvent.class));
        }

        @Test
        @DisplayName("corrida de concorrência (SaveResult não-inserted): sem TSS/TSB/reconciliação/evento")
        void corridaDeConcorrenciaSemEfeitosColaterais() {
            when(intervalsIcuConnectionService.conexaoAtiva(atleta.getId(), tenantId))
                    .thenReturn(Optional.of(new IntegracaoExterna()));
            TreinoRealizado mapeado = treinoMapeado();
            TreinoRealizado jaExistente = treinoMapeado();
            when(intervalsIcuActivityMapper.map(dto, atleta)).thenReturn(mapeado);
            when(treinoDedupHelper.saveIdempotent(mapeado, EXTERNAL_ID, atleta.getId()))
                    .thenReturn(new TreinoDedupHelper.SaveResult(jaExistente, false));

            TreinoRealizado resultado = persister.persistir(dto, atleta, tenantId, EXTERNAL_ID);

            assertThat(resultado).isSameAs(jaExistente);
            verify(tssCalculatorService, never()).calcularTss(any());
            verify(tsbService, never()).atualizarTsbDia(any(), any());
            verify(candidateSelector, never()).buscarCandidatos(any(), any());
            verify(reconciliationDecisionExecutor, never()).executar(any(), any(), any());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("reconciliação inline é chamada com o treino já inserido (CA3)")
        void reconciliacaoChamadaComTreinoInserido() {
            when(intervalsIcuConnectionService.conexaoAtiva(atleta.getId(), tenantId))
                    .thenReturn(Optional.of(new IntegracaoExterna()));
            TreinoRealizado mapeado = treinoMapeado();
            when(intervalsIcuActivityMapper.map(dto, atleta)).thenReturn(mapeado);
            when(treinoDedupHelper.saveIdempotent(mapeado, EXTERNAL_ID, atleta.getId()))
                    .thenReturn(new TreinoDedupHelper.SaveResult(mapeado, true));
            when(candidateSelector.buscarCandidatos(mapeado, tenantId)).thenReturn(List.of());
            when(reconciliationDecisionExecutor.executar(eq(mapeado), any(), eq(atleta)))
                    .thenReturn(new MatchingDecision(ReconciliationStatus.NAO_PLANEJADO, null, List.of(), "NO_CANDIDATES", "razão"));

            persister.persistir(dto, atleta, tenantId, EXTERNAL_ID);

            verify(candidateSelector).buscarCandidatos(mapeado, tenantId);
            verify(reconciliationDecisionExecutor).executar(eq(mapeado), any(), eq(atleta));
        }

        @Test
        @DisplayName("evento TreinoRegistradoEvent é publicado SOMENTE após a reconciliação ser computada (pre-mortem #10)")
        void eventoPublicadoApenasAposReconciliacao() {
            when(intervalsIcuConnectionService.conexaoAtiva(atleta.getId(), tenantId))
                    .thenReturn(Optional.of(new IntegracaoExterna()));
            TreinoRealizado mapeado = treinoMapeado();
            when(intervalsIcuActivityMapper.map(dto, atleta)).thenReturn(mapeado);
            when(treinoDedupHelper.saveIdempotent(mapeado, EXTERNAL_ID, atleta.getId()))
                    .thenReturn(new TreinoDedupHelper.SaveResult(mapeado, true));
            when(candidateSelector.buscarCandidatos(mapeado, tenantId)).thenReturn(List.of());
            when(reconciliationDecisionExecutor.executar(eq(mapeado), any(), eq(atleta)))
                    .thenReturn(new MatchingDecision(ReconciliationStatus.NAO_PLANEJADO, null, List.of(), "NO_CANDIDATES", "razão"));

            persister.persistir(dto, atleta, tenantId, EXTERNAL_ID);

            org.mockito.InOrder ordem = org.mockito.Mockito.inOrder(reconciliationDecisionExecutor, eventPublisher);
            ordem.verify(reconciliationDecisionExecutor).executar(eq(mapeado), any(), eq(atleta));
            ordem.verify(eventPublisher).publishEvent(any(br.com.menthoros.backend.events.TreinoRegistradoEvent.class));
        }
    }

    private TreinoRealizado treinoMapeado() {
        TreinoRealizado t = new TreinoRealizado();
        t.setId(UUID.randomUUID());
        t.setAtleta(atleta);
        t.setDataTreino(LocalDate.of(2026, 7, 16));
        return t;
    }
    @Nested
    @DisplayName("persistir — etapas vindas do mapper")
    class Etapas {

        @Test
        @DisplayName("as etapas do treino mapeado chegam intactas ao save — persistem por cascade")
        void etapasPersistemPorCascade() {
            when(intervalsIcuConnectionService.conexaoAtiva(atleta.getId(), tenantId))
                    .thenReturn(Optional.of(new IntegracaoExterna()));
            TreinoRealizado mapeado = treinoComEtapas(3);
            when(intervalsIcuActivityMapper.map(dto, atleta)).thenReturn(mapeado);
            when(treinoDedupHelper.saveIdempotent(mapeado, EXTERNAL_ID, atleta.getId()))
                    .thenReturn(new TreinoDedupHelper.SaveResult(mapeado, true));
            when(candidateSelector.buscarCandidatos(mapeado, tenantId)).thenReturn(List.of());

            TreinoRealizado salvo = persister.persistir(dto, atleta, tenantId, EXTERNAL_ID);

            // O persister nao mexe em etapas: quem anexa e o mapper, e o cascade persiste.
            assertThat(salvo.getEtapasRealizadas()).hasSize(3);
            verify(treinoDedupHelper).saveIdempotent(
                    org.mockito.ArgumentMatchers.argThat(t -> t.getEtapasRealizadas().size() == 3),
                    eq(EXTERNAL_ID), eq(atleta.getId()));
        }

        @Test
        @DisplayName("corrida de concorrencia: nao duplica etapas no registro vencedor")
        void corridaDeConcorrenciaNaoDuplicaEtapas() {
            when(intervalsIcuConnectionService.conexaoAtiva(atleta.getId(), tenantId))
                    .thenReturn(Optional.of(new IntegracaoExterna()));
            TreinoRealizado mapeado = treinoComEtapas(3);
            TreinoRealizado vencedor = treinoComEtapas(3);   // ja persistido por outra requisicao
            when(intervalsIcuActivityMapper.map(dto, atleta)).thenReturn(mapeado);
            when(treinoDedupHelper.saveIdempotent(mapeado, EXTERNAL_ID, atleta.getId()))
                    .thenReturn(new TreinoDedupHelper.SaveResult(vencedor, false));

            TreinoRealizado salvo = persister.persistir(dto, atleta, tenantId, EXTERNAL_ID);

            assertThat(salvo).isSameAs(vencedor);
            assertThat(salvo.getEtapasRealizadas()).hasSize(3);
            verify(tsbService, never()).atualizarTsbDia(any(), any());
        }

        @Test
        @DisplayName("activity sem intervalos preserva o comportamento anterior — treino sem etapas")
        void semIntervalosComportamentoInalterado() {
            when(intervalsIcuConnectionService.conexaoAtiva(atleta.getId(), tenantId))
                    .thenReturn(Optional.of(new IntegracaoExterna()));
            TreinoRealizado mapeado = treinoComEtapas(0);
            when(intervalsIcuActivityMapper.map(dto, atleta)).thenReturn(mapeado);
            when(treinoDedupHelper.saveIdempotent(mapeado, EXTERNAL_ID, atleta.getId()))
                    .thenReturn(new TreinoDedupHelper.SaveResult(mapeado, true));
            when(candidateSelector.buscarCandidatos(mapeado, tenantId)).thenReturn(List.of());

            TreinoRealizado salvo = persister.persistir(dto, atleta, tenantId, EXTERNAL_ID);

            assertThat(salvo.getEtapasRealizadas()).isEmpty();
            verify(tsbService).atualizarTsbDia(eq(atleta.getId()), any());
        }
    }

    private TreinoRealizado treinoComEtapas(int quantidade) {
        TreinoRealizado treino = new TreinoRealizado();
        treino.setId(UUID.randomUUID());
        treino.setDataTreino(LocalDate.of(2026, 7, 16));
        for (int i = 1; i <= quantidade; i++) {
            br.com.menthoros.backend.entity.EtapaRealizada etapa = new br.com.menthoros.backend.entity.EtapaRealizada();
            etapa.setOrdem(i);
            etapa.setTreinoRealizado(treino);
            treino.getEtapasRealizadas().add(etapa);
        }
        return treino;
    }
    @Nested
    @DisplayName("persistir — metrica de cobertura de etapas")
    class MetricaCobertura {

        @Test
        @DisplayName("import COM etapas conta como coberto, com tag do tenant")
        void importComEtapasContaCoberto() {
            stubInsertNovo(treinoComEtapas(3));

            persister.persistir(dto, atleta, tenantId, EXTERNAL_ID);

            assertThat(contador("com_etapas")).isEqualTo(1.0);
            assertThat(contador("sem_etapas")).isZero();
        }

        @Test
        @DisplayName("import SEM etapas conta como descoberto — e o que expoe a lacuna por assessoria")
        void importSemEtapasContaDescoberto() {
            stubInsertNovo(treinoComEtapas(0));

            persister.persistir(dto, atleta, tenantId, EXTERNAL_ID);

            assertThat(contador("sem_etapas")).isEqualTo(1.0);
            assertThat(contador("com_etapas")).isZero();
        }

        @Test
        @DisplayName("corrida de concorrencia nao conta duas vezes")
        void corridaNaoContaDuasVezes() {
            when(intervalsIcuConnectionService.conexaoAtiva(atleta.getId(), tenantId))
                    .thenReturn(Optional.of(new IntegracaoExterna()));
            TreinoRealizado mapeado = treinoComEtapas(2);
            when(intervalsIcuActivityMapper.map(dto, atleta)).thenReturn(mapeado);
            when(treinoDedupHelper.saveIdempotent(mapeado, EXTERNAL_ID, atleta.getId()))
                    .thenReturn(new TreinoDedupHelper.SaveResult(treinoComEtapas(2), false));

            persister.persistir(dto, atleta, tenantId, EXTERNAL_ID);

            assertThat(contador("com_etapas")).isZero();
            assertThat(contador("sem_etapas")).isZero();
        }

        private void stubInsertNovo(TreinoRealizado mapeado) {
            when(intervalsIcuConnectionService.conexaoAtiva(atleta.getId(), tenantId))
                    .thenReturn(Optional.of(new IntegracaoExterna()));
            when(intervalsIcuActivityMapper.map(dto, atleta)).thenReturn(mapeado);
            when(treinoDedupHelper.saveIdempotent(mapeado, EXTERNAL_ID, atleta.getId()))
                    .thenReturn(new TreinoDedupHelper.SaveResult(mapeado, true));
            when(candidateSelector.buscarCandidatos(mapeado, tenantId)).thenReturn(List.of());
        }

        private double contador(String resultado) {
            io.micrometer.core.instrument.Counter c = meterRegistry.find("intervals_icu.import.etapas")
                    .tag("tenant", tenantId.toString())
                    .tag("resultado", resultado)
                    .counter();
            return c == null ? 0.0 : c.count();
        }
    }
}
