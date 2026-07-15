package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.events.PlanoDeletadoEvent;
import br.com.menthoros.backend.services.IntervalsIcuConnectionService;
import br.com.menthoros.backend.services.WorkoutChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.time.LocalDate;
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
class IntervalsIcuPlanoDeletadoListenerTest {

    @Mock private IntervalsIcuConnectionService connectionService;
    @Mock private WorkoutChannel workoutChannel;

    private IntervalsIcuPlanoDeletadoListener listener;

    private UUID planoId;
    private UUID atletaId;
    private UUID tenantId;
    private LocalDate semanaInicio;
    private LocalDate semanaFim;
    private PlanoDeletadoEvent event;
    private IntegracaoExterna conexao;

    @BeforeEach
    void setUp() {
        planoId = UUID.randomUUID();
        atletaId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        semanaInicio = LocalDate.now();
        semanaFim = semanaInicio.plusDays(6);
        event = new PlanoDeletadoEvent(planoId, atletaId, tenantId, semanaInicio, semanaFim);

        conexao = new IntegracaoExterna();
        conexao.setId(UUID.randomUUID());
        conexao.setTenantId(tenantId);

        listener = new IntervalsIcuPlanoDeletadoListener(connectionService, workoutChannel);
    }

    @Nested
    @DisplayName("onPlanoDeletado")
    class OnPlanoDeletado {

        @Test
        @DisplayName("regra 3: sem conexão ativa retorna sem tocar no workoutChannel")
        void semConexaoAtivaNaoTocaWorkoutChannel() {
            when(connectionService.conexaoAtiva(atletaId, tenantId)).thenReturn(Optional.empty());

            listener.onPlanoDeletado(event);

            verifyNoInteractions(workoutChannel);
        }

        @Test
        @DisplayName("regra 3: com conexão ativa remove os eventos menthoros-* da janela do plano deletado")
        void comConexaoAtivaRemoveOrfaosDaJanela() {
            when(connectionService.conexaoAtiva(atletaId, tenantId)).thenReturn(Optional.of(conexao));

            listener.onPlanoDeletado(event);

            verify(workoutChannel).removerOrfaos(conexao, semanaInicio, semanaFim, Set.of());
        }

        @Test
        @DisplayName("regra 3: tenant do evento é usado diretamente, sem depender de TenantContext")
        void usaTenantDoEventoPorParametro() {
            when(connectionService.conexaoAtiva(atletaId, tenantId)).thenReturn(Optional.of(conexao));

            listener.onPlanoDeletado(event);

            verify(connectionService).conexaoAtiva(atletaId, tenantId);
        }

        @Test
        @DisplayName("regra 3: erro do connectionService é capturado e não propaga — limpeza é best-effort")
        void erroNaConsultaDaConexaoNaoPropaga() {
            when(connectionService.conexaoAtiva(atletaId, tenantId))
                    .thenThrow(new RuntimeException("timeout"));

            listener.onPlanoDeletado(event);

            verifyNoInteractions(workoutChannel);
        }

        @Test
        @DisplayName("regra 3: erro do workoutChannel é capturado e não propaga — deleção já commitou")
        void erroNoWorkoutChannelNaoPropaga() {
            when(connectionService.conexaoAtiva(atletaId, tenantId)).thenReturn(Optional.of(conexao));
            org.mockito.Mockito.doThrow(new RuntimeException("indisponível"))
                    .when(workoutChannel).removerOrfaos(any(), any(), any(), any());

            listener.onPlanoDeletado(event);

            verify(workoutChannel).removerOrfaos(conexao, semanaInicio, semanaFim, Set.of());
        }
    }

    // =========================================================================
    // Guard-rail — a deleção do plano nunca é afetada pela limpeza do intervals.icu
    // =========================================================================

    @Nested
    @DisplayName("guard-rail: anotações do listener")
    class GuardRailAnotacoes {

        // Se alguém tornar o listener síncrono (remover @Async), remover o AFTER_COMMIT ou tirar a
        // transação própria (REQUIRES_NEW), a limpeza passaria a rodar dentro/junto da transação de
        // deleção — e uma falha na limpeza derrubaria a deleção do plano. Estes asserts quebram nesse
        // caso.

        private Method onPlanoDeletado() throws NoSuchMethodException {
            return IntervalsIcuPlanoDeletadoListener.class.getMethod("onPlanoDeletado", PlanoDeletadoEvent.class);
        }

        @Test
        @DisplayName("@Async no executor dedicado intervalsIcuPushExecutor")
        void asyncNoExecutorDedicado() throws NoSuchMethodException {
            Async async = onPlanoDeletado().getAnnotation(Async.class);
            assertThat(async).as("@Async removida: a limpeza passaria a rodar síncrona na deleção").isNotNull();
            assertThat(async.value()).isEqualTo("intervalsIcuPushExecutor");
        }

        @Test
        @DisplayName("@TransactionalEventListener com phase AFTER_COMMIT")
        void listenerAposCommit() throws NoSuchMethodException {
            TransactionalEventListener listener = onPlanoDeletado().getAnnotation(TransactionalEventListener.class);
            assertThat(listener).as("@TransactionalEventListener removida").isNotNull();
            assertThat(listener.phase())
                    .as("sem AFTER_COMMIT a limpeza processaria eventos de transações que sofreram rollback")
                    .isEqualTo(TransactionPhase.AFTER_COMMIT);
        }

        @Test
        @DisplayName("@Transactional com propagation REQUIRES_NEW")
        void transacaoPropria() throws NoSuchMethodException {
            Transactional transactional = onPlanoDeletado().getAnnotation(Transactional.class);
            assertThat(transactional).as("@Transactional removida").isNotNull();
            assertThat(transactional.propagation())
                    .as("sem REQUIRES_NEW o listener não teria transação própria (AFTER_COMMIT não abre uma)")
                    .isEqualTo(Propagation.REQUIRES_NEW);
        }
    }
}
