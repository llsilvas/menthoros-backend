package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.input.BatchGeracaoPlanoInputDto;
import br.com.menthoros.backend.dto.output.BatchJobStatusOutputDto;
import br.com.menthoros.backend.dto.output.BatchLoteAceitoOutputDto;
import br.com.menthoros.backend.enums.BatchJobStatus;
import br.com.menthoros.backend.enums.ModoGeracaoPlano;
import br.com.menthoros.backend.exception.ResourceNotFoundException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.services.BatchPlanService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Testes do {@link CoachBatchPlanController}. A autorização ({@code @PreAuthorize},
 * requer TECNICO/ADMIN) e a validação ({@code @Valid}, 400 para lista vazia/>20) são
 * declarativas — validadas em runtime pelas camadas Security/MVC, como nos demais coach
 * controllers do módulo; aqui verificamos o mapeamento do resultado, o 202 + Location e
 * a propagação do 404.
 */
@ExtendWith(MockitoExtension.class)
class CoachBatchPlanControllerTest {

    @Mock
    private BatchPlanService batchPlanService;
    @InjectMocks
    private CoachBatchPlanController controller;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("gerarLote")
    class GerarLote {

        @Test
        @DisplayName("POST /planos/gerar-lote → 202 com jobId, totalAtletas e header Location")
        void retorna202ComLocation() {
            UUID jobId = UUID.randomUUID();
            when(batchPlanService.iniciarLote(any(), eq(tenantId)))
                    .thenReturn(new BatchLoteAceitoOutputDto(jobId, 3));

            ResponseEntity<BatchLoteAceitoOutputDto> resposta = controller.gerarLote(
                    new BatchGeracaoPlanoInputDto(
                            List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
                            ModoGeracaoPlano.PROXIMA_SEMANA));

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
            assertThat(resposta.getBody()).isNotNull();
            assertThat(resposta.getBody().jobId()).isEqualTo(jobId);
            assertThat(resposta.getBody().totalAtletas()).isEqualTo(3);
            assertThat(resposta.getHeaders().getLocation()).isNotNull();
            assertThat(resposta.getHeaders().getLocation().toString()).endsWith("/planos/lote/" + jobId);
        }
    }

    @Nested
    @DisplayName("consultarLote")
    class ConsultarLote {

        @Test
        @DisplayName("GET /planos/lote/{jobId} → 200 com o estado do job")
        void retorna200ComEstado() {
            UUID jobId = UUID.randomUUID();
            BatchJobStatusOutputDto status = new BatchJobStatusOutputDto(
                    jobId, BatchJobStatus.EM_PROGRESSO, 5, 2, 0, List.of(), List.of());
            when(batchPlanService.consultarStatus(jobId, tenantId)).thenReturn(status);

            ResponseEntity<BatchJobStatusOutputDto> resposta = controller.consultarLote(jobId);

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resposta.getBody()).isNotNull();
            assertThat(resposta.getBody().status()).isEqualTo(BatchJobStatus.EM_PROGRESSO);
            assertThat(resposta.getBody().gerados()).isEqualTo(2);
        }

        @Test
        @DisplayName("propaga ResourceNotFoundException (→ 404) para jobId de outro tenant")
        void propagaNotFound() {
            UUID jobId = UUID.randomUUID();
            when(batchPlanService.consultarStatus(jobId, tenantId))
                    .thenThrow(new ResourceNotFoundException("Job de geração em lote não encontrado"));

            assertThatThrownBy(() -> controller.consultarLote(jobId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
