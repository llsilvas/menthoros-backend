package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.events.WorkoutEditedAfterApprovalEvent;
import br.com.menthoros.backend.repository.IntegracaoExternaRepository;
import br.com.menthoros.backend.services.GarminConnectionService;
import br.com.menthoros.backend.services.impl.IntervalsIcuPushProcessor.ProcessamentoResultado;
import br.com.menthoros.backend.services.impl.IntervalsIcuPushProcessor.ResultadoPush;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkoutEditedPushListenerTest {

    @Mock private GarminConnectionService connectionService;
    @Mock private IntervalsIcuPushProcessor pushProcessor;
    @Mock private IntegracaoExternaRepository integracaoExternaRepository;
    @InjectMocks private WorkoutEditedPushListener listener;

    private WorkoutEditedAfterApprovalEvent event;
    private IntegracaoExterna conexao;

    @BeforeEach
    void setUp() {
        event = new WorkoutEditedAfterApprovalEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        conexao = new IntegracaoExterna();
        conexao.setPlataforma(FonteDados.GARMIN);
    }

    @Nested
    @DisplayName("onWorkoutEdited")
    class OnWorkoutEdited {

        @Test
        @DisplayName("é assíncrono no executor do push e roda só depois do commit")
        void assincronoAposCommit() throws NoSuchMethodException {
            Method m = WorkoutEditedPushListener.class.getMethod("onWorkoutEdited", WorkoutEditedAfterApprovalEvent.class);

            assertThat(m.getAnnotation(Async.class).value()).isEqualTo("intervalsIcuPushExecutor");
            assertThat(m.getAnnotation(TransactionalEventListener.class).phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
        }

        @Test
        @DisplayName("com canal: empurra só o treino editado e registra a sincronização da conexão")
        void comCanalEmpurraSoOTreino() {
            when(connectionService.conexaoDeCanal(event.atletaId(), event.tenantId())).thenReturn(Optional.of(conexao));
            when(pushProcessor.processar(event.treinoId(), event.tenantId(), conexao))
                    .thenReturn(ResultadoPush.simples(ProcessamentoResultado.PROCESSADO_SUCESSO));

            listener.onWorkoutEdited(event);

            verify(pushProcessor).processar(event.treinoId(), event.tenantId(), conexao);
            assertThat(conexao.getUltimaSincronizacao()).isNotNull();
            verify(integracaoExternaRepository).save(conexao);
        }

        @Test
        @DisplayName("erro de autenticação grava o lastSyncError da conexão")
        void erroAutenticacao() {
            when(connectionService.conexaoDeCanal(event.atletaId(), event.tenantId())).thenReturn(Optional.of(conexao));
            when(pushProcessor.processar(event.treinoId(), event.tenantId(), conexao))
                    .thenReturn(ResultadoPush.simples(ProcessamentoResultado.PROCESSADO_ERRO_AUTENTICACAO));

            listener.onWorkoutEdited(event);

            assertThat(conexao.getLastSyncError()).contains("autenticação");
            verify(integracaoExternaRepository).save(conexao);
        }

        @Test
        @DisplayName("claim perdido (outro push no ar) não mexe na conexão")
        void claimPerdidoNaoMexeNaConexao() {
            when(connectionService.conexaoDeCanal(event.atletaId(), event.tenantId())).thenReturn(Optional.of(conexao));
            when(pushProcessor.processar(event.treinoId(), event.tenantId(), conexao))
                    .thenReturn(ResultadoPush.simples(ProcessamentoResultado.CLAIM_PERDIDO));

            listener.onWorkoutEdited(event);

            verify(integracaoExternaRepository, never()).save(any());
        }

        @Test
        @DisplayName("sem canal (conexão caiu depois do commit): não empurra")
        void semCanalNaoEmpurra() {
            when(connectionService.conexaoDeCanal(event.atletaId(), event.tenantId())).thenReturn(Optional.empty());

            listener.onWorkoutEdited(event);

            verifyNoInteractions(pushProcessor, integracaoExternaRepository);
        }

        @Test
        @DisplayName("exceção do processor não escapa do listener: o scheduler recupera o AGUARDANDO_RETRY")
        void excecaoNaoEscapa() {
            when(connectionService.conexaoDeCanal(event.atletaId(), event.tenantId())).thenReturn(Optional.of(conexao));
            when(pushProcessor.processar(event.treinoId(), event.tenantId(), conexao))
                    .thenThrow(new IllegalStateException("boom"));

            listener.onWorkoutEdited(event);

            verify(integracaoExternaRepository, never()).save(any());
        }
    }
}
