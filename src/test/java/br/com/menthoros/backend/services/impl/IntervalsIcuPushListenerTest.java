package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.domain.workout.StructuredWorkout;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.enums.StatusSincronizacao;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.events.PlanoAprovadoEvent;
import br.com.menthoros.backend.repository.IntegracaoExternaRepository;
import br.com.menthoros.backend.repository.PlanoSemanalRepository;
import br.com.menthoros.backend.repository.TreinoPlanejadoRepository;
import br.com.menthoros.backend.services.IntervalsIcuConnectionService;
import br.com.menthoros.backend.services.PushResult;
import br.com.menthoros.backend.services.WorkoutChannel;
import br.com.menthoros.backend.services.helper.IntervalsIcuWorkoutConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntervalsIcuPushListenerTest {

    @Mock private IntervalsIcuConnectionService connectionService;
    @Mock private IntervalsIcuWorkoutConverter converter;
    @Mock private WorkoutChannel workoutChannel;
    @Mock private PlanoSemanalRepository planoSemanalRepository;
    @Mock private TreinoPlanejadoRepository treinoPlanejadoRepository;
    @Mock private IntegracaoExternaRepository integracaoExternaRepository;

    // IntervalsIcuPushProcessor não é mockado: é uma instância real construída com os MESMOS
    // mocks acima (converter/workoutChannel/treinoPlanejadoRepository) — o claim atômico e a
    // marcação de resultado foram extraídos para lá (compartilhado com o retry scheduler), mas os
    // testes deste listener continuam validando o fluxo ponta a ponta através dele.
    private IntervalsIcuPushListener listener;

    private UUID planoId;
    private UUID atletaId;
    private UUID tenantId;
    private PlanoAprovadoEvent event;
    private IntegracaoExterna conexao;

    @BeforeEach
    void setUp() {
        planoId = UUID.randomUUID();
        atletaId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        event = new PlanoAprovadoEvent(planoId, atletaId, tenantId);
        conexao = new IntegracaoExterna();
        conexao.setId(UUID.randomUUID());
        conexao.setTenantId(tenantId);

        IntervalsIcuPushProcessor pushProcessor =
                new IntervalsIcuPushProcessor(converter, workoutChannel, treinoPlanejadoRepository);
        listener = new IntervalsIcuPushListener(connectionService, pushProcessor, workoutChannel,
                planoSemanalRepository, treinoPlanejadoRepository, integracaoExternaRepository);
    }

    @Nested
    @DisplayName("onPlanoAprovado")
    class OnPlanoAprovado {

        @Test
        @DisplayName("regra 1: sem conexão ativa retorna sem tocar nos treinos")
        void semConexaoAtivaNaoTocaTreinos() {
            when(connectionService.conexaoAtiva(atletaId, tenantId)).thenReturn(Optional.empty());

            listener.onPlanoAprovado(event);

            verifyNoInteractions(planoSemanalRepository, treinoPlanejadoRepository, workoutChannel, converter,
                    integracaoExternaRepository);
        }

        @Test
        @DisplayName("regra 2: usa a instância recarregada fresca do treino, não a da transação pai")
        void recarregaTreinoFrescoDoBanco() {
            TreinoPlanejado stale = treino(UUID.randomUUID(), tenantId, "STALE-EXTERNAL-ID");
            TreinoPlanejado fresh = treino(stale.getId(), tenantId, null);
            PlanoSemanal plano = planoCom(List.of(stale));
            StructuredWorkout workout = workout();

            mocarConexaoEPlano(plano);
            when(treinoPlanejadoRepository.findByIdAndTenantId(stale.getId(), tenantId)).thenReturn(Optional.of(fresh));
            when(converter.converter(fresh)).thenReturn(Optional.of(workout));
            when(treinoPlanejadoRepository.saveAndFlush(fresh)).thenReturn(fresh);
            when(workoutChannel.push(conexao, workout, null)).thenReturn(PushResult.ok(111L));

            listener.onPlanoAprovado(event);

            verify(converter).converter(fresh);
            verify(converter, never()).converter(stale);
            assertThat(fresh.getExternalId()).isEqualTo("111");
        }

        @Test
        @DisplayName("regra 3: claim perdido por OptimisticLockingFailureException não impede os demais treinos")
        void claimPerdidoContinuaComOutrosTreinos() {
            TreinoPlanejado t1 = treino(UUID.randomUUID(), tenantId, null);
            TreinoPlanejado t2 = treino(UUID.randomUUID(), tenantId, null);
            PlanoSemanal plano = planoCom(List.of(t1, t2));
            StructuredWorkout workout = workout();

            mocarConexaoEPlano(plano);
            when(treinoPlanejadoRepository.findByIdAndTenantId(t1.getId(), tenantId)).thenReturn(Optional.of(t1));
            when(treinoPlanejadoRepository.findByIdAndTenantId(t2.getId(), tenantId)).thenReturn(Optional.of(t2));
            when(converter.converter(t1)).thenReturn(Optional.of(workout));
            when(converter.converter(t2)).thenReturn(Optional.of(workout));
            when(treinoPlanejadoRepository.saveAndFlush(t1)).thenThrow(new OptimisticLockingFailureException("stale"));
            when(treinoPlanejadoRepository.saveAndFlush(t2)).thenReturn(t2);
            when(workoutChannel.push(conexao, workout, null)).thenReturn(PushResult.ok(222L));

            listener.onPlanoAprovado(event);

            verify(treinoPlanejadoRepository, never()).save(t1);
            verify(treinoPlanejadoRepository).save(t2);
            assertThat(t2.getStatusSincronizacao()).isEqualTo(StatusSincronizacao.SINCRONIZADO);
        }

        @Test
        @DisplayName("regra 4: converter vazio pula o treino sem erro e sem mutação de estado")
        void converterVazioPulaSemEstado() {
            TreinoPlanejado t = treino(UUID.randomUUID(), tenantId, null);
            PlanoSemanal plano = planoCom(List.of(t));

            mocarConexaoEPlano(plano);
            when(treinoPlanejadoRepository.findByIdAndTenantId(t.getId(), tenantId)).thenReturn(Optional.of(t));
            when(converter.converter(t)).thenReturn(Optional.empty());

            listener.onPlanoAprovado(event);

            verify(treinoPlanejadoRepository, never()).saveAndFlush(t);
            verify(treinoPlanejadoRepository, never()).save(t);
            verify(workoutChannel, never()).push(any(), any(), any());
            verify(workoutChannel).removerOrfaos(conexao, plano.getSemanaInicio(), plano.getSemanaFim(), Set.of());
            assertThat(t.getStatusSincronizacao()).isEqualTo(StatusSincronizacao.PENDENTE);
        }

        @Test
        @DisplayName("regra 5: push com sucesso marca sincronizado e grava o externalId do evento")
        void pushSucessoMarcaSincronizado() {
            TreinoPlanejado t = treino(UUID.randomUUID(), tenantId, null);
            PlanoSemanal plano = planoCom(List.of(t));
            StructuredWorkout workout = workout();

            mocarConexaoEPlano(plano);
            when(treinoPlanejadoRepository.findByIdAndTenantId(t.getId(), tenantId)).thenReturn(Optional.of(t));
            when(converter.converter(t)).thenReturn(Optional.of(workout));
            when(treinoPlanejadoRepository.saveAndFlush(t)).thenReturn(t);
            when(workoutChannel.push(conexao, workout, null)).thenReturn(PushResult.ok(789L));

            listener.onPlanoAprovado(event);

            assertThat(t.getStatusSincronizacao()).isEqualTo(StatusSincronizacao.SINCRONIZADO);
            assertThat(t.getExternalId()).isEqualTo("789");
            verify(treinoPlanejadoRepository).save(t);
        }

        @Test
        @DisplayName("regra 5: push com falha grava statusErro e mensagem quando ainda não atingiu o limite")
        void pushFalhaGravaErroTemporario() {
            TreinoPlanejado t = treino(UUID.randomUUID(), tenantId, null);
            t.setTentativasSincronizacao(1);
            PlanoSemanal plano = planoCom(List.of(t));
            StructuredWorkout workout = workout();

            mocarConexaoEPlano(plano);
            when(treinoPlanejadoRepository.findByIdAndTenantId(t.getId(), tenantId)).thenReturn(Optional.of(t));
            when(converter.converter(t)).thenReturn(Optional.of(workout));
            when(treinoPlanejadoRepository.saveAndFlush(t)).thenReturn(t);
            when(workoutChannel.push(conexao, workout, null))
                    .thenReturn(PushResult.erro(StatusSincronizacao.ERRO_TEMPORARIO, "timeout"));

            listener.onPlanoAprovado(event);

            assertThat(t.getStatusSincronizacao()).isEqualTo(StatusSincronizacao.ERRO_TEMPORARIO);
            assertThat(t.getErroSincronizacao()).isEqualTo("timeout");
        }

        @Test
        @DisplayName("regra 5: atingir o limite de tentativas escala para ERRO_PERMANENTE mesmo com outro statusErro")
        void atingirLimiteForcaErroPermanente() {
            TreinoPlanejado t = treino(UUID.randomUUID(), tenantId, null);
            t.setTentativasSincronizacao(4); // registrarTentativaSincronizacao() leva a 5 => atingiuLimiteTentativas()
            PlanoSemanal plano = planoCom(List.of(t));
            StructuredWorkout workout = workout();

            mocarConexaoEPlano(plano);
            when(treinoPlanejadoRepository.findByIdAndTenantId(t.getId(), tenantId)).thenReturn(Optional.of(t));
            when(converter.converter(t)).thenReturn(Optional.of(workout));
            when(treinoPlanejadoRepository.saveAndFlush(t)).thenReturn(t);
            when(workoutChannel.push(conexao, workout, null))
                    .thenReturn(PushResult.erro(StatusSincronizacao.ERRO_TEMPORARIO, "timeout"));

            listener.onPlanoAprovado(event);

            assertThat(t.getStatusSincronizacao()).isEqualTo(StatusSincronizacao.ERRO_PERMANENTE);
        }

        @Test
        @DisplayName("regra 6: erro inesperado em um treino não aborta o processamento dos demais")
        void erroInesperadoNaoAbortaDemais() {
            TreinoPlanejado t1 = treino(UUID.randomUUID(), tenantId, null);
            TreinoPlanejado t2 = treino(UUID.randomUUID(), tenantId, null);
            PlanoSemanal plano = planoCom(List.of(t1, t2));
            StructuredWorkout workout = workout();

            mocarConexaoEPlano(plano);
            when(treinoPlanejadoRepository.findByIdAndTenantId(t1.getId(), tenantId)).thenReturn(Optional.of(t1));
            when(treinoPlanejadoRepository.findByIdAndTenantId(t2.getId(), tenantId)).thenReturn(Optional.of(t2));
            when(converter.converter(t1)).thenThrow(new RuntimeException("boom"));
            when(converter.converter(t2)).thenReturn(Optional.of(workout));
            when(treinoPlanejadoRepository.saveAndFlush(t2)).thenReturn(t2);
            when(workoutChannel.push(conexao, workout, null)).thenReturn(PushResult.ok(999L));

            listener.onPlanoAprovado(event);

            assertThat(t2.getStatusSincronizacao()).isEqualTo(StatusSincronizacao.SINCRONIZADO);
            verify(workoutChannel).push(conexao, workout, null);
        }

        @Test
        @DisplayName("regra 7: após os pushes chama removerOrfaos com a janela do plano e os externalIds "
                + "canônicos (menthoros-<treinoId>), nunca o eventId numérico do treino")
        void chamaRemoverOrfaosComExternalIdsAtuais() {
            TreinoPlanejado t1 = treino(UUID.randomUUID(), tenantId, null); // exportável, vai sincronizar agora
            TreinoPlanejado t2 = treino(UUID.randomUUID(), tenantId, "555"); // não mais exportável (ex.: virou descanso)
            LocalDate inicio = LocalDate.now();
            LocalDate fim = inicio.plusDays(6);
            PlanoSemanal plano = planoCom(List.of(t1, t2), inicio, fim);
            StructuredWorkout workout = workout();

            mocarConexaoEPlano(plano);
            when(treinoPlanejadoRepository.findByIdAndTenantId(t1.getId(), tenantId)).thenReturn(Optional.of(t1));
            when(treinoPlanejadoRepository.findByIdAndTenantId(t2.getId(), tenantId)).thenReturn(Optional.of(t2));
            when(converter.converter(t1)).thenReturn(Optional.of(workout));
            when(converter.converter(t2)).thenReturn(Optional.empty());
            when(treinoPlanejadoRepository.saveAndFlush(t1)).thenReturn(t1);
            when(workoutChannel.push(conexao, workout, null)).thenReturn(PushResult.ok(321L));

            listener.onPlanoAprovado(event);

            // IntervalsIcuAdapter#removerOrfaos compara contra o external_id canônico do evento
            // ("menthoros-<treinoId>"), nunca contra o eventId numérico que passa a ocupar
            // treino.getExternalId() após um push bem-sucedido (aqui, "321").
            verify(workoutChannel).removerOrfaos(conexao, inicio, fim, Set.of("menthoros-" + t1.getId()));
        }

        @Test
        @DisplayName("costura C1: push bem-sucedido alimenta removerOrfaos com \"menthoros-<treinoId>\", "
                + "nunca com o eventId numérico gravado em treino.getExternalId()")
        void pushSucessoAlimentaReconciliacaoComExternalIdCanonico() {
            TreinoPlanejado t = treino(UUID.randomUUID(), tenantId, null);
            PlanoSemanal plano = planoCom(List.of(t));
            StructuredWorkout workout = workout();

            mocarConexaoEPlano(plano);
            when(treinoPlanejadoRepository.findByIdAndTenantId(t.getId(), tenantId)).thenReturn(Optional.of(t));
            when(converter.converter(t)).thenReturn(Optional.of(workout));
            when(treinoPlanejadoRepository.saveAndFlush(t)).thenReturn(t);
            when(workoutChannel.push(conexao, workout, null)).thenReturn(PushResult.ok(111L));

            listener.onPlanoAprovado(event);

            assertThat(t.getExternalId()).isEqualTo("111");
            ArgumentCaptor<Set<String>> setCaptor = ArgumentCaptor.forClass(Set.class);
            verify(workoutChannel).removerOrfaos(any(), any(), any(), setCaptor.capture());
            assertThat(setCaptor.getValue())
                    .containsExactly("menthoros-" + t.getId())
                    .doesNotContain("111");
        }

        @Test
        @DisplayName("regra 8: ERRO_AUTENTICACAO grava lastSyncError na conexão")
        void erroAutenticacaoGravaLastSyncError() {
            TreinoPlanejado t = treino(UUID.randomUUID(), tenantId, null);
            PlanoSemanal plano = planoCom(List.of(t));
            StructuredWorkout workout = workout();

            mocarConexaoEPlano(plano);
            when(treinoPlanejadoRepository.findByIdAndTenantId(t.getId(), tenantId)).thenReturn(Optional.of(t));
            when(converter.converter(t)).thenReturn(Optional.of(workout));
            when(treinoPlanejadoRepository.saveAndFlush(t)).thenReturn(t);
            when(workoutChannel.push(conexao, workout, null))
                    .thenReturn(PushResult.erro(StatusSincronizacao.ERRO_AUTENTICACAO, "token expirado"));

            listener.onPlanoAprovado(event);

            ArgumentCaptor<IntegracaoExterna> captor = ArgumentCaptor.forClass(IntegracaoExterna.class);
            verify(integracaoExternaRepository).save(captor.capture());
            assertThat(captor.getValue().getLastSyncError()).containsIgnoringCase("autenticação");
        }

        @Test
        @DisplayName("I1: push com sucesso grava ultimaSincronizacao na conexao")
        void pushSucessoGravaUltimaSincronizacao() {
            TreinoPlanejado t = treino(UUID.randomUUID(), tenantId, null);
            PlanoSemanal plano = planoCom(List.of(t));
            StructuredWorkout workout = workout();

            mocarConexaoEPlano(plano);
            when(treinoPlanejadoRepository.findByIdAndTenantId(t.getId(), tenantId)).thenReturn(Optional.of(t));
            when(converter.converter(t)).thenReturn(Optional.of(workout));
            when(treinoPlanejadoRepository.saveAndFlush(t)).thenReturn(t);
            when(workoutChannel.push(conexao, workout, null)).thenReturn(PushResult.ok(654L));

            assertThat(conexao.getUltimaSincronizacao()).isNull();

            listener.onPlanoAprovado(event);

            ArgumentCaptor<IntegracaoExterna> captor = ArgumentCaptor.forClass(IntegracaoExterna.class);
            verify(integracaoExternaRepository).save(captor.capture());
            assertThat(captor.getValue().getUltimaSincronizacao()).isNotNull();
        }

        @Test
        @DisplayName("I1: lote todo com falha (não-autenticação) não grava ultimaSincronizacao nem toca a conexao")
        void loteTodoComFalhaNaoGravaUltimaSincronizacao() {
            TreinoPlanejado t = treino(UUID.randomUUID(), tenantId, null);
            PlanoSemanal plano = planoCom(List.of(t));
            StructuredWorkout workout = workout();

            mocarConexaoEPlano(plano);
            when(treinoPlanejadoRepository.findByIdAndTenantId(t.getId(), tenantId)).thenReturn(Optional.of(t));
            when(converter.converter(t)).thenReturn(Optional.of(workout));
            when(treinoPlanejadoRepository.saveAndFlush(t)).thenReturn(t);
            when(workoutChannel.push(conexao, workout, null))
                    .thenReturn(PushResult.erro(StatusSincronizacao.ERRO_TEMPORARIO, "timeout"));

            listener.onPlanoAprovado(event);

            assertThat(conexao.getUltimaSincronizacao()).isNull();
            verify(integracaoExternaRepository, never()).save(any());
        }

        @Test
        @DisplayName("guard-rail: throw inesperado do channel após o claim nunca deixa o treino em SINCRONIZANDO")
        void throwInesperadoAposClaimNaoDeixaSincronizandoOrfao() {
            // channel.push promete nunca lançar; se um adapter violar o contrato, o treino já
            // reclamado (SINCRONIZANDO persistido) precisa degradar para ERRO_TEMPORARIO — o
            // scheduler de retry (Task 10) não varre SINCRONIZANDO, então o estado ficaria órfão.
            TreinoPlanejado t1 = treino(UUID.randomUUID(), tenantId, null);
            TreinoPlanejado t2 = treino(UUID.randomUUID(), tenantId, null);
            PlanoSemanal plano = planoCom(List.of(t1, t2));
            StructuredWorkout workout = workout();

            mocarConexaoEPlano(plano);
            when(treinoPlanejadoRepository.findByIdAndTenantId(t1.getId(), tenantId)).thenReturn(Optional.of(t1));
            when(treinoPlanejadoRepository.findByIdAndTenantId(t2.getId(), tenantId)).thenReturn(Optional.of(t2));
            when(converter.converter(t1)).thenReturn(Optional.of(workout));
            when(converter.converter(t2)).thenReturn(Optional.of(workout));
            when(treinoPlanejadoRepository.saveAndFlush(t1)).thenReturn(t1);
            when(treinoPlanejadoRepository.saveAndFlush(t2)).thenReturn(t2);
            when(workoutChannel.push(conexao, workout, null))
                    .thenThrow(new RuntimeException("violação de contrato"))
                    .thenReturn(PushResult.ok(444L));

            listener.onPlanoAprovado(event);

            assertThat(t1.getStatusSincronizacao()).isEqualTo(StatusSincronizacao.ERRO_TEMPORARIO);
            assertThat(t1.getStatusSincronizacao()).isNotEqualTo(StatusSincronizacao.SINCRONIZANDO);
            assertThat(t1.getErroSincronizacao()).contains("violação de contrato");
            verify(treinoPlanejadoRepository).save(t1);
            // o segundo treino continua sendo processado normalmente
            assertThat(t2.getStatusSincronizacao()).isEqualTo(StatusSincronizacao.SINCRONIZADO);
        }

        @Test
        @DisplayName("regra 9: treino com tenant diferente do evento é ignorado com log de segurança")
        void tenantMismatchIgnoraTreino() {
            UUID outroTenant = UUID.randomUUID();
            TreinoPlanejado t = treino(UUID.randomUUID(), outroTenant, null);
            PlanoSemanal plano = planoCom(List.of(t));

            mocarConexaoEPlano(plano);

            listener.onPlanoAprovado(event);

            verify(treinoPlanejadoRepository, never()).findByIdAndTenantId(t.getId(), tenantId);
            verifyNoInteractions(converter);
            verify(workoutChannel, never()).push(any(), any(), any());
        }
    }

    // =========================================================================
    // Guard-rail spec 8.7 — a aprovação nunca falha por causa do push
    // =========================================================================

    @Nested
    @DisplayName("guard-rail spec 8.7: anotações do listener")
    class GuardRailAnotacoes {

        // Se alguém tornar o listener síncrono (remover @Async), remover o AFTER_COMMIT ou tirar a
        // transação própria (REQUIRES_NEW), o push passaria a rodar dentro/junto da transação de
        // aprovação — e uma falha de push derrubaria a aprovação. Estes asserts quebram nesse caso.

        private Method onPlanoAprovado() throws NoSuchMethodException {
            return IntervalsIcuPushListener.class.getMethod("onPlanoAprovado", PlanoAprovadoEvent.class);
        }

        @Test
        @DisplayName("@Async no executor dedicado intervalsIcuPushExecutor")
        void asyncNoExecutorDedicado() throws NoSuchMethodException {
            Async async = onPlanoAprovado().getAnnotation(Async.class);
            assertThat(async).as("@Async removida: o push passaria a rodar síncrono na aprovação").isNotNull();
            assertThat(async.value()).isEqualTo("intervalsIcuPushExecutor");
        }

        @Test
        @DisplayName("@TransactionalEventListener com phase AFTER_COMMIT")
        void listenerAposCommit() throws NoSuchMethodException {
            TransactionalEventListener listener = onPlanoAprovado().getAnnotation(TransactionalEventListener.class);
            assertThat(listener).as("@TransactionalEventListener removida").isNotNull();
            assertThat(listener.phase())
                    .as("sem AFTER_COMMIT o push processaria eventos de transações que sofreram rollback")
                    .isEqualTo(TransactionPhase.AFTER_COMMIT);
        }

        @Test
        @DisplayName("@Transactional com propagation REQUIRES_NEW")
        void transacaoPropria() throws NoSuchMethodException {
            Transactional transactional = onPlanoAprovado().getAnnotation(Transactional.class);
            assertThat(transactional).as("@Transactional removida").isNotNull();
            assertThat(transactional.propagation())
                    .as("sem REQUIRES_NEW o listener não teria transação própria (AFTER_COMMIT não abre uma)")
                    .isEqualTo(Propagation.REQUIRES_NEW);
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void mocarConexaoEPlano(PlanoSemanal plano) {
        when(connectionService.conexaoAtiva(atletaId, tenantId)).thenReturn(Optional.of(conexao));
        when(planoSemanalRepository.findByIdAndTenantId(planoId, tenantId)).thenReturn(Optional.of(plano));
    }

    private PlanoSemanal planoCom(List<TreinoPlanejado> treinos) {
        return planoCom(treinos, LocalDate.now(), LocalDate.now().plusDays(6));
    }

    private PlanoSemanal planoCom(List<TreinoPlanejado> treinos, LocalDate inicio, LocalDate fim) {
        PlanoSemanal plano = new PlanoSemanal();
        plano.setId(planoId);
        plano.setSemanaInicio(inicio);
        plano.setSemanaFim(fim);
        plano.setTreinosPlanejados(treinos);
        return plano;
    }

    private TreinoPlanejado treino(UUID id, UUID treinoTenantId, String externalId) {
        TreinoPlanejado t = new TreinoPlanejado();
        t.setId(id);
        t.setTenantId(treinoTenantId);
        t.setExternalId(externalId);
        t.setTipoTreino(TipoTreino.REGENERATIVO);
        t.setStatusSincronizacao(StatusSincronizacao.PENDENTE);
        t.setTentativasSincronizacao(0);
        return t;
    }

    private StructuredWorkout workout() {
        return new StructuredWorkout("menthoros-x", "TREINO", null, LocalDate.now(), "desc", List.of());
    }
}
