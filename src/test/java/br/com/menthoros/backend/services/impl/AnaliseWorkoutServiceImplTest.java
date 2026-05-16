package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.output.AnaliseWorkoutOutputDto;
import br.com.menthoros.backend.entity.AnaliseWorkout;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.AnaliseStatus;
import br.com.menthoros.backend.enums.PrimaryAnalysisCause;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.repository.AiWorkoutAnalysisRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnaliseWorkoutServiceImplTest {

    @Mock
    private AiWorkoutAnalysisRepository analiseRepository;

    @Mock
    private TreinoRealizadoRepository treinoRealizadoRepository;

    @InjectMocks
    private AnaliseWorkoutServiceImpl service;

    private UUID treinoId;
    private UUID tenantId;
    private TreinoRealizado treino;
    private AnaliseWorkout analiseCompleted;

    @BeforeEach
    void setUp() {
        treinoId = UUID.randomUUID();
        tenantId = UUID.randomUUID();

        treino = new TreinoRealizado();
        treino.setTenantId(tenantId);

        analiseCompleted = new AnaliseWorkout();
        analiseCompleted.setId(UUID.randomUUID());
        analiseCompleted.setTreinoRealizadoId(treinoId);
        analiseCompleted.setTenantId(tenantId);
        analiseCompleted.setStatus(AnaliseStatus.COMPLETED);
        analiseCompleted.setSummaryPt("Execução dentro do esperado");
        analiseCompleted.setPrimaryCause(PrimaryAnalysisCause.NORMAL);
        analiseCompleted.setExecutionScore(8);
        analiseCompleted.setTags(List.of("GOOD_EXECUTION"));
        analiseCompleted.setAnalyzedAt(Instant.now());
    }

    @Test
    void returns_completed_analysis_for_valid_tenant() {
        when(treinoRealizadoRepository.findById(treinoId)).thenReturn(Optional.of(treino));
        when(analiseRepository.findByTreinoRealizadoIdAndTenantId(treinoId, tenantId))
                .thenReturn(Optional.of(analiseCompleted));

        Optional<AnaliseWorkoutOutputDto> result = service.buscarAnalise(treinoId, tenantId);

        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(AnaliseStatus.COMPLETED);
        assertThat(result.get().executionScore()).isEqualTo(8);
        assertThat(result.get().primaryCause()).isEqualTo(PrimaryAnalysisCause.NORMAL);
    }

    @Test
    void returns_empty_when_analysis_is_pending() {
        AnaliseWorkout pending = new AnaliseWorkout();
        pending.setStatus(AnaliseStatus.PENDING);
        pending.setTenantId(tenantId);

        when(treinoRealizadoRepository.findById(treinoId)).thenReturn(Optional.of(treino));
        when(analiseRepository.findByTreinoRealizadoIdAndTenantId(treinoId, tenantId))
                .thenReturn(Optional.of(pending));

        Optional<AnaliseWorkoutOutputDto> result = service.buscarAnalise(treinoId, tenantId);

        assertThat(result).isEmpty();
    }

    @Test
    void returns_empty_when_no_analysis_exists_yet() {
        when(treinoRealizadoRepository.findById(treinoId)).thenReturn(Optional.of(treino));
        when(analiseRepository.findByTreinoRealizadoIdAndTenantId(treinoId, tenantId))
                .thenReturn(Optional.empty());

        Optional<AnaliseWorkoutOutputDto> result = service.buscarAnalise(treinoId, tenantId);

        assertThat(result).isEmpty();
    }

    @Test
    void throws_not_found_when_treino_belongs_to_different_tenant() {
        TreinoRealizado outroTreino = new TreinoRealizado();
        outroTreino.setTenantId(UUID.randomUUID()); // tenant diferente

        when(treinoRealizadoRepository.findById(treinoId)).thenReturn(Optional.of(outroTreino));

        assertThatThrownBy(() -> service.buscarAnalise(treinoId, tenantId))
                .isInstanceOf(DomainNotFoundException.class);
    }

    @Test
    void throws_not_found_when_treino_does_not_exist() {
        when(treinoRealizadoRepository.findById(treinoId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarAnalise(treinoId, tenantId))
                .isInstanceOf(DomainNotFoundException.class);
    }

    @Test
    void returns_failed_analysis_when_status_is_failed() {
        AnaliseWorkout failed = new AnaliseWorkout();
        failed.setId(UUID.randomUUID());
        failed.setTreinoRealizadoId(treinoId);
        failed.setTenantId(tenantId);
        failed.setStatus(AnaliseStatus.FAILED);
        failed.setErrorMessage("Timeout ao chamar Claude API");
        failed.setAnalyzedAt(Instant.now());

        when(treinoRealizadoRepository.findById(treinoId)).thenReturn(Optional.of(treino));
        when(analiseRepository.findByTreinoRealizadoIdAndTenantId(treinoId, tenantId))
                .thenReturn(Optional.of(failed));

        Optional<AnaliseWorkoutOutputDto> result = service.buscarAnalise(treinoId, tenantId);

        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(AnaliseStatus.FAILED);
    }
}
