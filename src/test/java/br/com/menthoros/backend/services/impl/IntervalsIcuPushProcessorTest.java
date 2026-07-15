package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.domain.workout.StructuredWorkout;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.enums.StatusSincronizacao;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.repository.TreinoPlanejadoRepository;
import br.com.menthoros.backend.services.PushResult;
import br.com.menthoros.backend.services.WorkoutChannel;
import br.com.menthoros.backend.services.helper.IntervalsIcuWorkoutConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Fronteira transacional (hardening): {@link IntervalsIcuPushProcessor} usa um
 * {@code TransactionTemplate} PRÓPRIO (REQUIRES_NEW) construído a partir do
 * {@link PlatformTransactionManager} injetado. Um {@code PlatformTransactionManager} mockado
 * (todos os métodos no-op) é suficiente aqui: o {@code TransactionTemplate} real delega
 * start/commit/rollback ao mock, mas executa o callback (claim/marcação) inline e de forma
 * síncrona — os mocks de converter/channel/repository continuam sendo os únicos pontos de
 * verificação de comportamento.
 */
@ExtendWith(MockitoExtension.class)
class IntervalsIcuPushProcessorTest {

    private static final String PLATAFORMA = "INTERVALS_ICU";

    @Mock private IntervalsIcuWorkoutConverter converter;
    @Mock private WorkoutChannel workoutChannel;
    @Mock private TreinoPlanejadoRepository treinoPlanejadoRepository;
    @Mock private PlatformTransactionManager transactionManager;

    private IntervalsIcuPushProcessor processor;

    private UUID treinoId;
    private UUID tenantId;
    private IntegracaoExterna conexao;

    @BeforeEach
    void setUp() {
        treinoId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        conexao = new IntegracaoExterna();
        conexao.setId(UUID.randomUUID());
        conexao.setTenantId(tenantId);

        processor = new IntervalsIcuPushProcessor(converter, workoutChannel, treinoPlanejadoRepository,
                transactionManager);
    }

    @Nested
    @DisplayName("processar")
    class Processar {

        @Test
        @DisplayName("treino não encontrado no tenant retorna NAO_ENCONTRADO sem qualquer mutação")
        void naoEncontradoRetornaSemMutacao() {
            when(treinoPlanejadoRepository.findByIdAndTenantId(treinoId, tenantId)).thenReturn(Optional.empty());

            IntervalsIcuPushProcessor.ResultadoPush resultado = processor.processar(treinoId, tenantId, conexao);

            assertThat(resultado.tipo()).isEqualTo(IntervalsIcuPushProcessor.ProcessamentoResultado.NAO_ENCONTRADO);
            assertThat(resultado.criadoNovo()).isFalse();
            assertThat(resultado.eventId()).isNull();
            verify(treinoPlanejadoRepository, never()).save(any());
            verify(treinoPlanejadoRepository, never()).saveAndFlush(any());
            verify(workoutChannel, never()).push(any(), any(), any());
        }

        @Test
        @DisplayName("não exportável sem vínculo prévio (nunca sincronizado) retorna NAO_EXPORTAVEL sem save")
        void naoExportavelSemEstadoPrevioNaoSalva() {
            TreinoPlanejado t = treino(null, StatusSincronizacao.PENDENTE);
            when(treinoPlanejadoRepository.findByIdAndTenantId(treinoId, tenantId)).thenReturn(Optional.of(t));
            when(converter.converter(t)).thenReturn(Optional.empty());

            IntervalsIcuPushProcessor.ResultadoPush resultado = processor.processar(treinoId, tenantId, conexao);

            assertThat(resultado.tipo()).isEqualTo(IntervalsIcuPushProcessor.ProcessamentoResultado.NAO_EXPORTAVEL);
            verify(treinoPlanejadoRepository, never()).save(any());
            verify(workoutChannel, never()).push(any(), any(), any());
        }

        @Test
        @DisplayName("não exportável com externalId/status sincronizado reseta o vínculo (NAO_SINCRONIZADO, "
                + "externalId nulo) e salva")
        void naoExportavelComEstadoSincronizadoReseta() {
            TreinoPlanejado t = treino("777", StatusSincronizacao.SINCRONIZADO);
            when(treinoPlanejadoRepository.findByIdAndTenantId(treinoId, tenantId)).thenReturn(Optional.of(t));
            when(converter.converter(t)).thenReturn(Optional.empty());

            IntervalsIcuPushProcessor.ResultadoPush resultado = processor.processar(treinoId, tenantId, conexao);

            assertThat(resultado.tipo()).isEqualTo(IntervalsIcuPushProcessor.ProcessamentoResultado.NAO_EXPORTAVEL);
            assertThat(t.getStatusSincronizacao()).isEqualTo(StatusSincronizacao.NAO_SINCRONIZADO);
            assertThat(t.getExternalId()).isNull();
            verify(treinoPlanejadoRepository).save(t);
            verify(workoutChannel, never()).push(any(), any(), any());
        }

        @Test
        @DisplayName("claim: registrarTentativa + SINCRONIZANDO + saveAndFlush acontecem ANTES do push")
        void claimAntesDoPush() {
            TreinoPlanejado t = treino(null, StatusSincronizacao.PENDENTE);
            StructuredWorkout workout = workout();
            AtomicReference<StatusSincronizacao> statusNoClaim = new AtomicReference<>();
            AtomicReference<Integer> tentativasNoClaim = new AtomicReference<>();

            when(treinoPlanejadoRepository.findByIdAndTenantId(treinoId, tenantId)).thenReturn(Optional.of(t));
            when(converter.converter(t)).thenReturn(Optional.of(workout));
            when(treinoPlanejadoRepository.saveAndFlush(t)).thenAnswer(invocation -> {
                TreinoPlanejado arg = invocation.getArgument(0);
                statusNoClaim.set(arg.getStatusSincronizacao());
                tentativasNoClaim.set(arg.getTentativasSincronizacao());
                return arg;
            });
            when(workoutChannel.push(eq(conexao), eq(workout), isNull())).thenReturn(PushResult.okCriado(111L));

            processor.processar(treinoId, tenantId, conexao);

            assertThat(statusNoClaim.get()).isEqualTo(StatusSincronizacao.SINCRONIZANDO);
            assertThat(tentativasNoClaim.get()).isEqualTo(1);

            InOrder ordem = inOrder(treinoPlanejadoRepository, workoutChannel);
            ordem.verify(treinoPlanejadoRepository).saveAndFlush(t);
            ordem.verify(workoutChannel).push(eq(conexao), eq(workout), isNull());
        }

        @Test
        @DisplayName("saveAndFlush lança OptimisticLockingFailureException retorna CLAIM_PERDIDO sem push")
        void claimPerdidoNaoChamaPush() {
            TreinoPlanejado t = treino(null, StatusSincronizacao.PENDENTE);
            StructuredWorkout workout = workout();

            when(treinoPlanejadoRepository.findByIdAndTenantId(treinoId, tenantId)).thenReturn(Optional.of(t));
            when(converter.converter(t)).thenReturn(Optional.of(workout));
            when(treinoPlanejadoRepository.saveAndFlush(t)).thenThrow(new OptimisticLockingFailureException("stale"));

            IntervalsIcuPushProcessor.ResultadoPush resultado = processor.processar(treinoId, tenantId, conexao);

            assertThat(resultado.tipo()).isEqualTo(IntervalsIcuPushProcessor.ProcessamentoResultado.CLAIM_PERDIDO);
            assertThat(resultado.criadoNovo()).isFalse();
            assertThat(resultado.eventId()).isNull();
            verify(workoutChannel, never()).push(any(), any(), any());
        }

        @Test
        @DisplayName("push com sucesso (okCriado) marca sincronizado, criadoNovo=true, eventId e externalId numérico")
        void sucessoOkCriadoMarcaSincronizado() {
            TreinoPlanejado t = treino(null, StatusSincronizacao.PENDENTE);
            StructuredWorkout workout = workout();

            when(treinoPlanejadoRepository.findByIdAndTenantId(treinoId, tenantId)).thenReturn(Optional.of(t));
            when(converter.converter(t)).thenReturn(Optional.of(workout));
            when(treinoPlanejadoRepository.saveAndFlush(t)).thenReturn(t);
            when(workoutChannel.push(eq(conexao), eq(workout), isNull())).thenReturn(PushResult.okCriado(111L));

            IntervalsIcuPushProcessor.ResultadoPush resultado = processor.processar(treinoId, tenantId, conexao);

            assertThat(resultado.tipo()).isEqualTo(IntervalsIcuPushProcessor.ProcessamentoResultado.PROCESSADO_SUCESSO);
            assertThat(resultado.criadoNovo()).isTrue();
            assertThat(resultado.eventId()).isEqualTo(111L);
            assertThat(t.getStatusSincronizacao()).isEqualTo(StatusSincronizacao.SINCRONIZADO);
            assertThat(t.getExternalId()).isEqualTo("111");
            verify(treinoPlanejadoRepository).save(t);
        }

        @Test
        @DisplayName("push com sucesso (okAtualizado) retorna criadoNovo=false")
        void sucessoOkAtualizadoCriadoNovoFalse() {
            TreinoPlanejado t = treino("111", StatusSincronizacao.ERRO_TEMPORARIO);
            StructuredWorkout workout = workout();

            when(treinoPlanejadoRepository.findByIdAndTenantId(treinoId, tenantId)).thenReturn(Optional.of(t));
            when(converter.converter(t)).thenReturn(Optional.of(workout));
            when(treinoPlanejadoRepository.saveAndFlush(t)).thenReturn(t);
            when(workoutChannel.push(eq(conexao), eq(workout), eq(111L))).thenReturn(PushResult.okAtualizado(111L));

            IntervalsIcuPushProcessor.ResultadoPush resultado = processor.processar(treinoId, tenantId, conexao);

            assertThat(resultado.tipo()).isEqualTo(IntervalsIcuPushProcessor.ProcessamentoResultado.PROCESSADO_SUCESSO);
            assertThat(resultado.criadoNovo()).isFalse();
            assertThat(resultado.eventId()).isEqualTo(111L);
        }

        @Test
        @DisplayName("erro de autenticação retorna PROCESSADO_ERRO_AUTENTICACAO")
        void erroAutenticacaoRetornaTipoDedicado() {
            TreinoPlanejado t = treino(null, StatusSincronizacao.PENDENTE);
            StructuredWorkout workout = workout();

            when(treinoPlanejadoRepository.findByIdAndTenantId(treinoId, tenantId)).thenReturn(Optional.of(t));
            when(converter.converter(t)).thenReturn(Optional.of(workout));
            when(treinoPlanejadoRepository.saveAndFlush(t)).thenReturn(t);
            when(workoutChannel.push(eq(conexao), eq(workout), isNull()))
                    .thenReturn(PushResult.erro(StatusSincronizacao.ERRO_AUTENTICACAO, "token expirado"));

            IntervalsIcuPushProcessor.ResultadoPush resultado = processor.processar(treinoId, tenantId, conexao);

            assertThat(resultado.tipo())
                    .isEqualTo(IntervalsIcuPushProcessor.ProcessamentoResultado.PROCESSADO_ERRO_AUTENTICACAO);
            assertThat(resultado.criadoNovo()).isFalse();
            assertThat(resultado.eventId()).isNull();
            assertThat(t.getStatusSincronizacao()).isEqualTo(StatusSincronizacao.ERRO_AUTENTICACAO);
        }

        @Test
        @DisplayName("erro com limite de tentativas esgotado escala o treino para ERRO_PERMANENTE")
        void erroComLimiteEsgotadoEscalaParaErroPermanente() {
            TreinoPlanejado t = treino(null, StatusSincronizacao.ERRO_TEMPORARIO);
            t.setTentativasSincronizacao(4); // registrarTentativaSincronizacao() leva a 5 => atingiuLimiteTentativas()
            StructuredWorkout workout = workout();

            when(treinoPlanejadoRepository.findByIdAndTenantId(treinoId, tenantId)).thenReturn(Optional.of(t));
            when(converter.converter(t)).thenReturn(Optional.of(workout));
            when(treinoPlanejadoRepository.saveAndFlush(t)).thenReturn(t);
            when(workoutChannel.push(eq(conexao), eq(workout), isNull()))
                    .thenReturn(PushResult.erro(StatusSincronizacao.ERRO_TEMPORARIO, "timeout"));

            IntervalsIcuPushProcessor.ResultadoPush resultado = processor.processar(treinoId, tenantId, conexao);

            assertThat(resultado.tipo()).isEqualTo(IntervalsIcuPushProcessor.ProcessamentoResultado.PROCESSADO_ERRO);
            assertThat(t.getStatusSincronizacao()).isEqualTo(StatusSincronizacao.ERRO_PERMANENTE);
        }

        @Test
        @DisplayName("channel LANÇA (violação de contrato) degrada para ERRO_TEMPORARIO e nunca deixa o "
                + "treino preso em SINCRONIZANDO")
        void channelLancaDegradaParaErroTemporario() {
            TreinoPlanejado t = treino(null, StatusSincronizacao.PENDENTE);
            StructuredWorkout workout = workout();

            when(treinoPlanejadoRepository.findByIdAndTenantId(treinoId, tenantId)).thenReturn(Optional.of(t));
            when(converter.converter(t)).thenReturn(Optional.of(workout));
            when(treinoPlanejadoRepository.saveAndFlush(t)).thenReturn(t);
            when(workoutChannel.push(eq(conexao), eq(workout), isNull()))
                    .thenThrow(new RuntimeException("violação de contrato"));

            IntervalsIcuPushProcessor.ResultadoPush resultado = processor.processar(treinoId, tenantId, conexao);

            assertThat(resultado.tipo()).isEqualTo(IntervalsIcuPushProcessor.ProcessamentoResultado.PROCESSADO_ERRO);
            assertThat(t.getStatusSincronizacao())
                    .isEqualTo(StatusSincronizacao.ERRO_TEMPORARIO)
                    .isNotEqualTo(StatusSincronizacao.SINCRONIZANDO);
            assertThat(t.getErroSincronizacao()).contains("violação de contrato");
            verify(treinoPlanejadoRepository).save(t);
        }

        @Test
        @DisplayName("treino some entre a TX-A (claim) e a TX-B (marcação): resultado descartado sem NPE")
        void treinoSomeEntreClaimEMarcacaoSemNpe() {
            TreinoPlanejado t = treino(null, StatusSincronizacao.PENDENTE);
            StructuredWorkout workout = workout();

            when(treinoPlanejadoRepository.findByIdAndTenantId(treinoId, tenantId))
                    .thenReturn(Optional.of(t))
                    .thenReturn(Optional.empty());
            when(converter.converter(t)).thenReturn(Optional.of(workout));
            when(treinoPlanejadoRepository.saveAndFlush(t)).thenReturn(t);
            when(workoutChannel.push(eq(conexao), eq(workout), isNull())).thenReturn(PushResult.okCriado(999L));

            assertThatCode(() -> processor.processar(treinoId, tenantId, conexao)).doesNotThrowAnyException();

            verify(treinoPlanejadoRepository, never()).save(any());
        }

        @Test
        @DisplayName("externalId não numérico é tratado como primeiro push (eventIdArmazenado null)")
        void externalIdNaoNumericoTratadoComoPrimeiroPush() {
            TreinoPlanejado t = treino("nao-numerico", StatusSincronizacao.ERRO_TEMPORARIO);
            StructuredWorkout workout = workout();

            when(treinoPlanejadoRepository.findByIdAndTenantId(treinoId, tenantId)).thenReturn(Optional.of(t));
            when(converter.converter(t)).thenReturn(Optional.of(workout));
            when(treinoPlanejadoRepository.saveAndFlush(t)).thenReturn(t);
            when(workoutChannel.push(eq(conexao), eq(workout), isNull())).thenReturn(PushResult.okCriado(555L));

            IntervalsIcuPushProcessor.ResultadoPush resultado = processor.processar(treinoId, tenantId, conexao);

            assertThat(resultado.tipo()).isEqualTo(IntervalsIcuPushProcessor.ProcessamentoResultado.PROCESSADO_SUCESSO);
            verify(workoutChannel).push(eq(conexao), eq(workout), isNull());
        }
    }

    @Nested
    @DisplayName("tenantValido")
    class TenantValido {

        @Test
        @DisplayName("tenants iguais retorna true")
        void tenantsIguaisRetornaTrue() {
            assertThat(IntervalsIcuPushProcessor.tenantValido(treinoId, tenantId, tenantId)).isTrue();
        }

        @Test
        @DisplayName("tenants divergentes retorna false")
        void tenantsDivergentesRetornaFalse() {
            assertThat(IntervalsIcuPushProcessor.tenantValido(treinoId, tenantId, UUID.randomUUID())).isFalse();
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private TreinoPlanejado treino(String externalId, StatusSincronizacao status) {
        TreinoPlanejado t = new TreinoPlanejado();
        t.setId(treinoId);
        t.setTenantId(tenantId);
        t.setExternalId(externalId);
        t.setTipoTreino(TipoTreino.REGENERATIVO);
        t.setStatusSincronizacao(status);
        t.setTentativasSincronizacao(0);
        return t;
    }

    private StructuredWorkout workout() {
        return new StructuredWorkout("menthoros-x", "TREINO", null, LocalDate.now(), "desc", List.of());
    }
}
