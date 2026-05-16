package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.output.AnaliseWorkoutOutputDto;
import br.com.menthoros.backend.enums.AnaliseStatus;
import br.com.menthoros.backend.enums.PrimaryAnalysisCause;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.services.AnaliseWorkoutService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnaliseWorkoutControllerTest {

    @Mock
    private AnaliseWorkoutService analiseWorkoutService;

    @InjectMocks
    private AnaliseWorkoutController controller;

    private UUID treinoId;
    private UUID tenantId;
    private AnaliseWorkoutOutputDto analiseDto;

    @BeforeEach
    void setUp() {
        treinoId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);

        analiseDto = new AnaliseWorkoutOutputDto(
                UUID.randomUUID(),
                treinoId,
                AnaliseStatus.COMPLETED,
                "Execução dentro do esperado",
                "Análise técnica detalhada do treino",
                PrimaryAnalysisCause.NORMAL,
                "Continue o plano atual",
                List.of("GOOD_EXECUTION"),
                8,
                "RPE delta 0 indica execução precisa",
                Instant.now()
        );
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void returns_200_with_completed_analysis() {
        when(analiseWorkoutService.buscarAnalise(treinoId, tenantId))
                .thenReturn(Optional.of(analiseDto));

        ResponseEntity<AnaliseWorkoutOutputDto> response = controller.getAnalise(treinoId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(AnaliseStatus.COMPLETED);
        assertThat(response.getBody().executionScore()).isEqualTo(8);
    }

    @Test
    void returns_204_when_analysis_is_pending_or_absent() {
        when(analiseWorkoutService.buscarAnalise(treinoId, tenantId))
                .thenReturn(Optional.empty());

        ResponseEntity<AnaliseWorkoutOutputDto> response = controller.getAnalise(treinoId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void propagates_not_found_when_treino_belongs_to_other_tenant() {
        when(analiseWorkoutService.buscarAnalise(treinoId, tenantId))
                .thenThrow(new DomainNotFoundException("Treino não encontrado"));

        assertThatThrownBy(() -> controller.getAnalise(treinoId))
                .isInstanceOf(DomainNotFoundException.class)
                .hasMessageContaining("Treino não encontrado");
    }

    @Test
    void delegates_tenant_resolution_to_tenant_context() {
        UUID differentTenant = UUID.randomUUID();
        TenantContext.setTenantId(differentTenant);

        when(analiseWorkoutService.buscarAnalise(treinoId, differentTenant))
                .thenReturn(Optional.of(analiseDto));

        ResponseEntity<AnaliseWorkoutOutputDto> response = controller.getAnalise(treinoId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
