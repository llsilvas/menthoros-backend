package br.com.menthoros.backend.services;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.BatchPlanJob;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.enums.BatchJobStatus;
import br.com.menthoros.backend.enums.ModoGeracaoPlano;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.exception.LLMException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.BatchPlanJobRepository;
import br.com.menthoros.backend.repository.PlanoSemanalRepository;
import br.com.menthoros.backend.services.helper.LlmConcurrencyLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("BatchPlanProcessor")
class BatchPlanProcessorTest {

    @Mock
    private BatchPlanJobRepository jobRepository;
    @Mock
    private AtletaRepository atletaRepository;
    @Mock
    private PlanoSemanalRepository planoSemanalRepository;
    @Mock
    private PlanoService planoService;
    @Mock
    private LlmConcurrencyLimiter llmConcurrencyLimiter;
    @Mock
    private TransactionTemplate transactionTemplate;

    private BatchPlanProcessor processor;

    private UUID jobId;
    private UUID tenantId;

    @BeforeEach
    void setUp() throws InterruptedException {
        // Executor síncrono (Runnable::run) — as subtasks rodam inline no teste.
        processor = new BatchPlanProcessor(
                Runnable::run, jobRepository, atletaRepository, planoSemanalRepository,
                planoService, llmConcurrencyLimiter, new ObjectMapper(), transactionTemplate);

        jobId = UUID.randomUUID();
        tenantId = UUID.randomUUID();

        // finalizarJob: carrega o job e persiste dentro do TransactionTemplate.
        BatchPlanJob job = new BatchPlanJob();
        job.setId(jobId);
        job.setTenantId(tenantId);
        lenient().when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        doAnswer(inv -> {
            Consumer<TransactionStatus> c = inv.getArgument(0);
            c.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        // limiter roda o supplier passado.
        lenient().when(llmConcurrencyLimiter.executar(any())).thenAnswer(inv -> {
            Supplier<?> s = inv.getArgument(0);
            return s.get();
        });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("processarLote")
    class ProcessarLote {

        @Test
        @DisplayName("gera planos para todos os atletas válidos e conclui como CONCLUIDO")
        void geraTodosComSucesso() {
            UUID a1 = atletaValido("Ana");
            UUID a2 = atletaValido("Bia");
            when(planoService.gerarPlanoTreino(any(), any())).thenReturn(planoComId());

            processor.processarLote(jobId, List.of(a1, a2), ModoGeracaoPlano.PROXIMA_SEMANA, tenantId);

            verify(jobRepository, org.mockito.Mockito.times(2)).incrementarGerados(jobId);
            verify(jobRepository, never()).incrementarErros(jobId);
            assertThat(statusFinalPersistido()).isEqualTo(BatchJobStatus.CONCLUIDO);
        }

        @Test
        @DisplayName("registra erro individual sem abortar o lote (CONCLUIDO_COM_ERROS)")
        void erroIndividualNaoAborta() {
            UUID ok = atletaValido("Ana");
            UUID falha = atletaValido("Bia");
            when(planoService.gerarPlanoTreino(eq(ok), any())).thenReturn(planoComId());
            when(planoService.gerarPlanoTreino(eq(falha), any()))
                    .thenThrow(new LLMException("timeout"));

            processor.processarLote(jobId, List.of(ok, falha), ModoGeracaoPlano.PROXIMA_SEMANA, tenantId);

            verify(jobRepository).incrementarGerados(jobId);
            verify(jobRepository).incrementarErros(jobId);
            assertThat(statusFinalPersistido()).isEqualTo(BatchJobStatus.CONCLUIDO_COM_ERROS);
        }

        @Test
        @DisplayName("atleta de outro tenant → motivo 'Atleta não encontrado', sem chamar o LLM")
        void atletaOutroTenant() {
            UUID id = UUID.randomUUID();
            when(atletaRepository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.empty());

            processor.processarLote(jobId, List.of(id), ModoGeracaoPlano.PROXIMA_SEMANA, tenantId);

            verify(planoService, never()).gerarPlanoTreino(any(), any());
            verify(jobRepository).incrementarErros(jobId);
            assertThat(motivoErroPersistido()).isEqualTo(BatchPlanProcessor.MOTIVO_ATLETA_NAO_ENCONTRADO);
        }

        @Test
        @DisplayName("atleta com plano existente na semana → 'Plano já existe' via fast-path, sem LLM")
        void planoJaExisteFastPath() {
            UUID id = atletaValido("Ana");
            LocalDate semana = LocalDate.of(2026, 7, 6);
            when(planoService.calcularSemanaInicioAlvo(id, ModoGeracaoPlano.PROXIMA_SEMANA)).thenReturn(semana);
            when(planoSemanalRepository.findByAtletaIdAndSemanaInicio(id, semana))
                    .thenReturn(Optional.of(new PlanoSemanal()));

            processor.processarLote(jobId, List.of(id), ModoGeracaoPlano.PROXIMA_SEMANA, tenantId);

            verify(planoService, never()).gerarPlanoTreino(any(), any());
            verify(jobRepository).incrementarErros(jobId);
            assertThat(motivoErroPersistido()).isEqualTo(BatchPlanProcessor.MOTIVO_PLANO_JA_EXISTE);
        }

        @Test
        @DisplayName("DataIntegrityViolationException (corrida de lotes) → 'Plano já existe'")
        void corridaEntreLotes() {
            UUID id = atletaValido("Ana");
            when(planoService.gerarPlanoTreino(eq(id), any()))
                    .thenThrow(new DataIntegrityViolationException("uk_plano_semanal_atleta_semana_ativo"));

            processor.processarLote(jobId, List.of(id), ModoGeracaoPlano.PROXIMA_SEMANA, tenantId);

            verify(jobRepository).incrementarErros(jobId);
            assertThat(motivoErroPersistido()).isEqualTo(BatchPlanProcessor.MOTIVO_PLANO_JA_EXISTE);
        }

        @Test
        @DisplayName("DomainRuleViolationException de duplicidade → 'Plano já existe'")
        void dupViaDomainRule() {
            UUID id = atletaValido("Ana");
            when(planoService.gerarPlanoTreino(eq(id), any()))
                    .thenThrow(new DomainRuleViolationException("Já existe um plano semanal para o atleta"));

            processor.processarLote(jobId, List.of(id), ModoGeracaoPlano.PROXIMA_SEMANA, tenantId);

            assertThat(motivoErroPersistido()).isEqualTo(BatchPlanProcessor.MOTIVO_PLANO_JA_EXISTE);
        }

        @Test
        @DisplayName("DomainRuleViolationException não-duplicidade → 'Erro ao gerar plano'")
        void erroDominioNaoDuplicidade() {
            UUID id = atletaValido("Ana");
            when(planoService.gerarPlanoTreino(eq(id), any()))
                    .thenThrow(new DomainRuleViolationException("Não há dias disponíveis para treino"));

            processor.processarLote(jobId, List.of(id), ModoGeracaoPlano.PROXIMA_SEMANA, tenantId);

            assertThat(motivoErroPersistido()).isEqualTo(BatchPlanProcessor.MOTIVO_ERRO_GERACAO);
        }

        @Test
        @DisplayName("marca EM_PROGRESSO no início e limpa o TenantContext ao final")
        void transicaoStatusELimpezaTenant() {
            UUID id = atletaValido("Ana");
            when(planoService.gerarPlanoTreino(any(), any())).thenReturn(planoComId());

            processor.processarLote(jobId, List.of(id), ModoGeracaoPlano.PROXIMA_SEMANA, tenantId);

            verify(jobRepository).atualizarStatus(jobId, BatchJobStatus.EM_PROGRESSO);
            assertThat(TenantContext.getTenantId()).isNull();
        }
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private UUID atletaValido(String nome) {
        UUID id = UUID.randomUUID();
        Atleta atleta = new Atleta();
        atleta.setNome(nome);
        when(atletaRepository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(atleta));
        return id;
    }

    private PlanoSemanal planoComId() {
        PlanoSemanal plano = new PlanoSemanal();
        plano.setId(UUID.randomUUID());
        return plano;
    }

    private BatchJobStatus statusFinalPersistido() {
        return jobPersistido().getStatus();
    }

    private String motivoErroPersistido() {
        BatchPlanJob job = jobPersistido();
        try {
            BatchResultadoJson r = new ObjectMapper().readValue(job.getResultado(), BatchResultadoJson.class);
            return r.erros().get(0).motivo();
        } catch (Exception e) {
            throw new AssertionError("resultado inválido: " + job.getResultado(), e);
        }
    }

    private BatchPlanJob jobPersistido() {
        ArgumentCaptor<BatchPlanJob> captor = ArgumentCaptor.forClass(BatchPlanJob.class);
        verify(jobRepository).save(captor.capture());
        return captor.getValue();
    }
}
