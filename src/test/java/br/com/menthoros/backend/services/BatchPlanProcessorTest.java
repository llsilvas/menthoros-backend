package br.com.menthoros.backend.services;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.BatchPlanJob;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.enums.BatchJobStatus;
import br.com.menthoros.backend.enums.ModoGeracaoPlano;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.exception.LLMException;
import br.com.menthoros.backend.exception.PlanoJaExistenteException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.BatchPlanJobRepository;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

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
@DisplayName("BatchPlanProcessor")
class BatchPlanProcessorTest {

    @Mock
    private BatchPlanJobRepository jobRepository;
    @Mock
    private AtletaRepository atletaRepository;
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
                Runnable::run, jobRepository, atletaRepository,
                planoService, llmConcurrencyLimiter, new ObjectMapper(), transactionTemplate);

        jobId = UUID.randomUUID();
        tenantId = UUID.randomUUID();

        // finalizarJob: carrega o job (tenant-scoped) e persiste dentro do TransactionTemplate.
        BatchPlanJob job = new BatchPlanJob();
        job.setId(jobId);
        job.setTenantId(tenantId);
        lenient().when(jobRepository.findByIdAndTenantId(jobId, tenantId)).thenReturn(Optional.of(job));
        lenient().doAnswer(inv -> {
            Consumer<TransactionStatus> c = inv.getArgument(0);
            c.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        // limiter roda o supplier passado (só nos testes que chamam gerarPlanoTreino).
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
            assertThat(geradosPersistidos())
                    .extracting(br.com.menthoros.backend.dto.output.BatchJobStatusOutputDto.BatchGeradoItemDto::atletaNome)
                    .containsExactlyInAnyOrder("Ana", "Bia");
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
        @DisplayName("atleta com plano ativo na semana → 'Plano já existe' via fast-path, sem LLM")
        void planoJaExisteFastPath() {
            UUID id = atletaValido("Ana");
            when(planoService.existePlanoParaSemana(id, ModoGeracaoPlano.PROXIMA_SEMANA)).thenReturn(true);

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
        @DisplayName("PlanoJaExistenteException (corrida fast-path/checagem interna) → 'Plano já existe'")
        void dupViaExcecaoTipada() {
            UUID id = atletaValido("Ana");
            when(planoService.gerarPlanoTreino(eq(id), any()))
                    .thenThrow(new PlanoJaExistenteException("Já existe um plano semanal ativo"));

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
        return resultadoPersistido().erros().get(0).motivo();
    }

    private List<br.com.menthoros.backend.dto.output.BatchJobStatusOutputDto.BatchGeradoItemDto> geradosPersistidos() {
        return resultadoPersistido().gerados();
    }

    private BatchResultadoJson resultadoPersistido() {
        BatchPlanJob job = jobPersistido();
        try {
            return new ObjectMapper().readValue(job.getResultado(), BatchResultadoJson.class);
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
