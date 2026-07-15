package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.enums.StatusSincronizacao;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.repository.TreinoPlanejadoRepository;
import br.com.menthoros.backend.services.IntervalsIcuConnectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.repository.Query;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntervalsIcuRetrySchedulerImplTest {

    @Mock private IntervalsIcuConnectionService connectionService;
    @Mock private IntervalsIcuPushProcessor pushProcessor;
    @Mock private TreinoPlanejadoRepository treinoPlanejadoRepository;

    @InjectMocks private IntervalsIcuRetrySchedulerImpl scheduler;

    private UUID tenantId;
    private Atleta atleta;
    private IntegracaoExterna conexao;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();

        Assessoria assessoria = new Assessoria();
        assessoria.setId(tenantId);

        atleta = new Atleta();
        atleta.setId(UUID.randomUUID());
        atleta.setAssessoria(assessoria);

        conexao = new IntegracaoExterna();
        conexao.setId(UUID.randomUUID());
        conexao.setTenantId(tenantId);
    }

    @Nested
    @DisplayName("reprocessarPendentes")
    class ReprocessarPendentes {

        @Test
        @DisplayName("nenhum candidato: não interage com conexão nem com o processor")
        void semCandidatosNaoInterage() {
            when(treinoPlanejadoRepository.findAllAguardandoRetryIntervalsIcu()).thenReturn(List.of());

            scheduler.reprocessarPendentes();

            verifyNoInteractions(connectionService, pushProcessor);
        }

        @Test
        @DisplayName("elegível (janela vencida, sem limite, conexão ativa) delega o push ao IntervalsIcuPushProcessor")
        void delegaAoProcessorQuandoElegivel() {
            TreinoPlanejado t = treino(StatusSincronizacao.ERRO_TEMPORARIO, 1, null);
            when(treinoPlanejadoRepository.findAllAguardandoRetryIntervalsIcu()).thenReturn(List.of(t));
            when(connectionService.conexaoAtiva(atleta.getId(), tenantId)).thenReturn(Optional.of(conexao));

            scheduler.reprocessarPendentes();

            verify(pushProcessor).processar(t.getId(), tenantId, conexao);
        }

        @Test
        @DisplayName("regra 3: limite de tentativas esgotado escala para ERRO_PERMANENTE sem chamada de rede")
        void limiteEsgotadoEscalaParaErroPermanenteSemRede() {
            TreinoPlanejado t = treino(StatusSincronizacao.ERRO_TEMPORARIO, 5, null);
            when(treinoPlanejadoRepository.findAllAguardandoRetryIntervalsIcu()).thenReturn(List.of(t));

            scheduler.reprocessarPendentes();

            assertThat(t.getStatusSincronizacao()).isEqualTo(StatusSincronizacao.ERRO_PERMANENTE);
            verify(treinoPlanejadoRepository).save(t);
            verifyNoInteractions(connectionService, pushProcessor);
        }

        @Test
        @DisplayName("regra 3: limite esgotado escala mesmo quando a janela de retry ainda não venceu")
        void limiteEsgotadoEscalaMesmoComJanelaAindaNaoVencida() {
            TreinoPlanejado t = treino(StatusSincronizacao.ERRO_LIMITE_RATE, 5, LocalDateTime.now());
            when(treinoPlanejadoRepository.findAllAguardandoRetryIntervalsIcu()).thenReturn(List.of(t));

            scheduler.reprocessarPendentes();

            assertThat(t.getStatusSincronizacao()).isEqualTo(StatusSincronizacao.ERRO_PERMANENTE);
            verifyNoInteractions(connectionService, pushProcessor);
        }

        @Test
        @DisplayName("regra 2: janela de retry (5min) ainda não vencida pula sem mutação de estado")
        void janelaNaoVencidaPulaSemMutacao() {
            TreinoPlanejado t = treino(StatusSincronizacao.ERRO_TEMPORARIO, 1, LocalDateTime.now());
            when(treinoPlanejadoRepository.findAllAguardandoRetryIntervalsIcu()).thenReturn(List.of(t));

            scheduler.reprocessarPendentes();

            assertThat(t.getStatusSincronizacao()).isEqualTo(StatusSincronizacao.ERRO_TEMPORARIO);
            verify(treinoPlanejadoRepository, never()).save(t);
            verifyNoInteractions(connectionService, pushProcessor);
        }

        @Test
        @DisplayName("janela vencida (mais de 5 minutos desde a última tentativa) libera o retry")
        void janelaVencidaLiberaRetry() {
            TreinoPlanejado t = treino(StatusSincronizacao.ERRO_TEMPORARIO, 1, LocalDateTime.now().minusMinutes(6));
            when(treinoPlanejadoRepository.findAllAguardandoRetryIntervalsIcu()).thenReturn(List.of(t));
            when(connectionService.conexaoAtiva(atleta.getId(), tenantId)).thenReturn(Optional.of(conexao));

            scheduler.reprocessarPendentes();

            verify(pushProcessor).processar(t.getId(), tenantId, conexao);
        }

        @Test
        @DisplayName("regra 8: atleta sem conexão ativa pula sem tocar o estado do treino")
        void semConexaoAtivaPulaSemTocarEstado() {
            TreinoPlanejado t = treino(StatusSincronizacao.AGUARDANDO_RETRY, 1, null);
            when(treinoPlanejadoRepository.findAllAguardandoRetryIntervalsIcu()).thenReturn(List.of(t));
            when(connectionService.conexaoAtiva(atleta.getId(), tenantId)).thenReturn(Optional.empty());

            scheduler.reprocessarPendentes();

            assertThat(t.getStatusSincronizacao()).isEqualTo(StatusSincronizacao.AGUARDANDO_RETRY);
            verify(treinoPlanejadoRepository, never()).save(t);
            verifyNoInteractions(pushProcessor);
        }

        @Test
        @DisplayName("regra 6: tenant do treino divergente da assessoria do atleta é ignorado com log de segurança")
        void tenantMismatchIgnoraTreino() {
            UUID outroTenant = UUID.randomUUID();
            TreinoPlanejado t = treino(StatusSincronizacao.ERRO_TEMPORARIO, 1, null);
            t.setTenantId(outroTenant);
            when(treinoPlanejadoRepository.findAllAguardandoRetryIntervalsIcu()).thenReturn(List.of(t));

            scheduler.reprocessarPendentes();

            verifyNoInteractions(connectionService, pushProcessor);
            verify(treinoPlanejadoRepository, never()).save(t);
        }

        @Test
        @DisplayName("treino sem atleta resolvido é pulado sem chamar conexão/processor")
        void semAtletaResolvidoPula() {
            TreinoPlanejado t = treino(StatusSincronizacao.ERRO_TEMPORARIO, 1, null);
            t.setAtleta(null);
            when(treinoPlanejadoRepository.findAllAguardandoRetryIntervalsIcu()).thenReturn(List.of(t));

            scheduler.reprocessarPendentes();

            verifyNoInteractions(connectionService, pushProcessor);
        }

        @Test
        @DisplayName("regra 5: erro inesperado (throw do processor) em um treino não aborta o processamento dos demais")
        void erroInesperadoNaoAbortaDemais() {
            TreinoPlanejado t1 = treino(StatusSincronizacao.ERRO_TEMPORARIO, 1, null);
            TreinoPlanejado t2 = treino(StatusSincronizacao.ERRO_TEMPORARIO, 1, null);
            when(treinoPlanejadoRepository.findAllAguardandoRetryIntervalsIcu()).thenReturn(List.of(t1, t2));
            when(connectionService.conexaoAtiva(atleta.getId(), tenantId)).thenReturn(Optional.of(conexao));
            when(pushProcessor.processar(t1.getId(), tenantId, conexao)).thenThrow(new RuntimeException("boom"));

            scheduler.reprocessarPendentes();

            verify(pushProcessor).processar(t2.getId(), tenantId, conexao);
        }

        @Test
        @DisplayName("regra 4: claim perdido (CLAIM_PERDIDO retornado pelo processor) não gera erro nem aborta o batch")
        void claimPerdidoNaoGeraErro() {
            TreinoPlanejado t1 = treino(StatusSincronizacao.AGUARDANDO_RETRY, 1, null);
            TreinoPlanejado t2 = treino(StatusSincronizacao.AGUARDANDO_RETRY, 1, null);
            when(treinoPlanejadoRepository.findAllAguardandoRetryIntervalsIcu()).thenReturn(List.of(t1, t2));
            when(connectionService.conexaoAtiva(atleta.getId(), tenantId)).thenReturn(Optional.of(conexao));
            when(pushProcessor.processar(t1.getId(), tenantId, conexao))
                    .thenReturn(IntervalsIcuPushProcessor.ResultadoPush.simples(
                            IntervalsIcuPushProcessor.ProcessamentoResultado.CLAIM_PERDIDO));

            scheduler.reprocessarPendentes();

            verify(pushProcessor).processar(t1.getId(), tenantId, conexao);
            verify(pushProcessor).processar(t2.getId(), tenantId, conexao);
        }
    }

    @Nested
    @DisplayName("guard-rail: configuração do @Scheduled")
    class GuardRailAnotacao {

        @Test
        @DisplayName("fixedDelayString PT15M e initialDelayString PT5M")
        void configuracaoDoScheduled() throws NoSuchMethodException {
            Scheduled scheduled = IntervalsIcuRetrySchedulerImpl.class
                    .getMethod("reprocessarPendentes")
                    .getAnnotation(Scheduled.class);

            assertThat(scheduled).as("@Scheduled removida do reprocessarPendentes").isNotNull();
            assertThat(scheduled.fixedDelayString()).isEqualTo("PT15M");
            assertThat(scheduled.initialDelayString()).isEqualTo("PT5M");
        }
    }

    // =========================================================================
    // Regra 1 (spec 3.3 + 8.2): a query nunca inclui SINCRONIZANDO nem
    // PENDENTE/NAO_SINCRONIZADO — verificado contra o texto real do @Query da
    // repository (fonte da verdade), cobrindo TODOS os StatusSincronizacao.
    // =========================================================================

    @Nested
    @DisplayName("regra 1: seleção de findAllAguardandoRetryIntervalsIcu")
    class SelecaoDeEstados {

        @ParameterizedTest(name = "{0}")
        @EnumSource(StatusSincronizacao.class)
        @DisplayName("a query IN inclui APENAS AGUARDANDO_RETRY/ERRO_TEMPORARIO/ERRO_LIMITE_RATE")
        void querySelecionaApenasEstadosDeRetry(StatusSincronizacao status) throws NoSuchMethodException {
            String jpql = TreinoPlanejadoRepository.class
                    .getMethod("findAllAguardandoRetryIntervalsIcu")
                    .getAnnotation(Query.class)
                    .value();

            boolean esperado = status == StatusSincronizacao.AGUARDANDO_RETRY
                    || status == StatusSincronizacao.ERRO_TEMPORARIO
                    || status == StatusSincronizacao.ERRO_LIMITE_RATE;

            boolean presenteNaQuery = Pattern.compile("\\b" + Pattern.quote(status.name()) + "\\b")
                    .matcher(jpql)
                    .find();

            assertThat(presenteNaQuery)
                    .as("status %s deveria %sfazer parte da query de retry", status, esperado ? "" : "NUNCA ")
                    .isEqualTo(esperado);
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private TreinoPlanejado treino(StatusSincronizacao status, int tentativas, LocalDateTime ultimaTentativa) {
        TreinoPlanejado t = new TreinoPlanejado();
        t.setId(UUID.randomUUID());
        t.setTenantId(tenantId);
        t.setAtleta(atleta);
        t.setTipoTreino(TipoTreino.REGENERATIVO);
        t.setStatusSincronizacao(status);
        t.setTentativasSincronizacao(tentativas);
        t.setUltimaTentativaSincronizacao(ultimaTentativa);
        return t;
    }
}
