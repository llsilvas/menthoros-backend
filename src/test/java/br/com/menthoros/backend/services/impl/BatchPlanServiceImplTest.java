package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.input.BatchGeracaoPlanoInputDto;
import br.com.menthoros.backend.dto.output.BatchJobStatusOutputDto;
import br.com.menthoros.backend.entity.BatchPlanJob;
import br.com.menthoros.backend.enums.BatchJobStatus;
import br.com.menthoros.backend.enums.ModoGeracaoPlano;
import br.com.menthoros.backend.exception.ResourceNotFoundException;
import br.com.menthoros.backend.repository.BatchPlanJobRepository;
import br.com.menthoros.backend.services.BatchPlanProcessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BatchPlanServiceImpl")
class BatchPlanServiceImplTest {

    @Mock
    private BatchPlanJobRepository jobRepository;
    @Mock
    private BatchPlanProcessor batchPlanProcessor;

    private BatchPlanServiceImpl service;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        // ObjectMapper real: exercita a (de)serialização de verdade.
        service = new BatchPlanServiceImpl(jobRepository, batchPlanProcessor, new ObjectMapper());
        tenantId = UUID.randomUUID();
    }

    @Nested
    @DisplayName("iniciarLote")
    class IniciarLote {

        @Test
        @DisplayName("cria o job e retorna o jobId")
        void criaJobERetornaId() {
            UUID atletaId = UUID.randomUUID();
            UUID jobId = UUID.randomUUID();
            when(jobRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> {
                BatchPlanJob j = inv.getArgument(0);
                j.setId(jobId);
                return j;
            });

            UUID resultado = service.iniciarLote(
                    new BatchGeracaoPlanoInputDto(List.of(atletaId), ModoGeracaoPlano.PROXIMA_SEMANA), tenantId);

            assertThat(resultado).isEqualTo(jobId);
        }

        @Test
        @DisplayName("deduplica atletaIds repetidos — totalAtletas reflete a contagem distinta")
        void deduplicaAtletas() {
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();
            ArgumentCaptor<BatchPlanJob> jobCaptor = ArgumentCaptor.forClass(BatchPlanJob.class);
            when(jobRepository.save(jobCaptor.capture())).thenAnswer(inv -> {
                BatchPlanJob j = inv.getArgument(0);
                j.setId(UUID.randomUUID());
                return j;
            });

            service.iniciarLote(
                    new BatchGeracaoPlanoInputDto(List.of(a, a, b), ModoGeracaoPlano.PROXIMA_SEMANA), tenantId);

            assertThat(jobCaptor.getValue().getTotalAtletas()).isEqualTo(2);
        }

        @Test
        @DisplayName("delega o processamento ao bean assíncrono com a lista deduplicada")
        void delegaAoProcessor() {
            UUID a = UUID.randomUUID();
            UUID jobId = UUID.randomUUID();
            when(jobRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> {
                BatchPlanJob j = inv.getArgument(0);
                j.setId(jobId);
                return j;
            });

            service.iniciarLote(
                    new BatchGeracaoPlanoInputDto(List.of(a, a), ModoGeracaoPlano.SEMANA_ATUAL), tenantId);

            verify(batchPlanProcessor).processarLote(
                    eq(jobId), eq(List.of(a)), eq(ModoGeracaoPlano.SEMANA_ATUAL), eq(tenantId));
        }
    }

    @Nested
    @DisplayName("consultarStatus")
    class ConsultarStatus {

        @Test
        @DisplayName("retorna o estado atual do job; detalhes vazios durante o progresso")
        void retornaEstadoAtual() {
            UUID jobId = UUID.randomUUID();
            BatchPlanJob job = new BatchPlanJob();
            job.setId(jobId);
            job.setTenantId(tenantId);
            job.setStatus(BatchJobStatus.EM_PROGRESSO);
            job.setTotalAtletas(5);
            job.setGerados(3);
            job.setErros(1);
            when(jobRepository.findByIdAndTenantId(jobId, tenantId)).thenReturn(Optional.of(job));

            BatchJobStatusOutputDto dto = service.consultarStatus(jobId, tenantId);

            assertThat(dto.status()).isEqualTo(BatchJobStatus.EM_PROGRESSO);
            assertThat(dto.gerados()).isEqualTo(3);
            assertThat(dto.erros()).isEqualTo(1);
            assertThat(dto.geradosDetalhes()).isEmpty();
            assertThat(dto.errosDetalhes()).isEmpty();
        }

        @Test
        @DisplayName("preenche os detalhes a partir do resultado JSON no estado terminal")
        void preencheDetalhesNoTerminal() {
            UUID jobId = UUID.randomUUID();
            UUID atletaId = UUID.randomUUID();
            BatchPlanJob job = new BatchPlanJob();
            job.setId(jobId);
            job.setTenantId(tenantId);
            job.setStatus(BatchJobStatus.CONCLUIDO_COM_ERROS);
            job.setTotalAtletas(2);
            job.setGerados(1);
            job.setErros(1);
            job.setResultado("{\"gerados\":[{\"atletaId\":\"" + atletaId
                    + "\",\"planoId\":\"" + UUID.randomUUID() + "\",\"atletaNome\":\"Ana\"}],"
                    + "\"erros\":[{\"atletaId\":\"" + UUID.randomUUID() + "\",\"motivo\":\"Atleta não encontrado\"}]}");
            when(jobRepository.findByIdAndTenantId(jobId, tenantId)).thenReturn(Optional.of(job));

            BatchJobStatusOutputDto dto = service.consultarStatus(jobId, tenantId);

            assertThat(dto.geradosDetalhes()).hasSize(1);
            assertThat(dto.geradosDetalhes().get(0).atletaNome()).isEqualTo("Ana");
            assertThat(dto.errosDetalhes()).hasSize(1);
            assertThat(dto.errosDetalhes().get(0).motivo()).isEqualTo("Atleta não encontrado");
        }

        @Test
        @DisplayName("lança ResourceNotFoundException para jobId de outro tenant")
        void lancaNotFoundParaOutroTenant() {
            UUID jobId = UUID.randomUUID();
            when(jobRepository.findByIdAndTenantId(jobId, tenantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.consultarStatus(jobId, tenantId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
