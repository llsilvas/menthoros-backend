package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.output.TreinoPlanejadoOutputDto;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.entity.PlannedWorkoutPostApprovalEdit;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.enums.TreinoExecucaoStatus;
import br.com.menthoros.backend.enums.WorkoutEditReason;
import br.com.menthoros.backend.mapper.TreinoMapper;
import br.com.menthoros.backend.repository.PlannedWorkoutPostApprovalEditRepository;
import br.com.menthoros.backend.security.AuthenticatedPrincipalResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostApprovalEditRecorderTest {

    private static final Instant AGORA = Instant.parse("2026-11-04T15:00:00Z");

    @Mock private TreinoMapper treinoMapper;
    @Mock private AuthenticatedPrincipalResolver principalResolver;
    @Mock private PlannedWorkoutPostApprovalEditRepository repository;

    private PostApprovalEditRecorder recorder;
    private final UUID tenantId = UUID.randomUUID();
    private PlanoSemanal plano;
    private TreinoPlanejado treino;

    @BeforeEach
    void setUp() {
        recorder = new PostApprovalEditRecorder(treinoMapper, new ObjectMapper(), principalResolver, repository);
        plano = new PlanoSemanal();
        plano.setId(UUID.randomUUID());
        treino = new TreinoPlanejado();
        treino.setId(UUID.randomUUID());
        treino.setTenantId(tenantId);
    }

    @Nested
    @DisplayName("snapshot")
    class Snapshot {

        @Test
        @DisplayName("serializa o DTO de saída do treino")
        void serializaDto() {
            when(treinoMapper.toOutputDto(treino)).thenReturn(dto("Antes"));

            assertThat(recorder.snapshot(treino)).contains("\"descricao\":\"Antes\"");
        }
    }

    @Nested
    @DisplayName("record")
    class Record {

        @Test
        @DisplayName("grava antes, depois, autor, instante, motivo e tenant (CA11b)")
        void gravaComMotivo() {
            when(treinoMapper.toOutputDto(treino)).thenReturn(dto("Depois"));
            when(principalResolver.getCurrentSubject()).thenReturn("coach-sub");

            recorder.record(plano, treino, "{\"descricao\":\"Antes\"}", WorkoutEditReason.LOAD, AGORA);

            ArgumentCaptor<PlannedWorkoutPostApprovalEdit> captor =
                    ArgumentCaptor.forClass(PlannedWorkoutPostApprovalEdit.class);
            verify(repository).save(captor.capture());
            PlannedWorkoutPostApprovalEdit edit = captor.getValue();
            assertThat(edit.getTenantId()).isEqualTo(tenantId);
            assertThat(edit.getPlannedWorkoutId()).isEqualTo(treino.getId());
            assertThat(edit.getWeeklyPlanId()).isEqualTo(plano.getId());
            assertThat(edit.getEditedBy()).isEqualTo("coach-sub");
            assertThat(edit.getEditedAt()).isEqualTo(AGORA);
            assertThat(edit.getReason()).isEqualTo(WorkoutEditReason.LOAD);
            assertThat(edit.getSnapshotBefore()).isEqualTo("{\"descricao\":\"Antes\"}");
            assertThat(edit.getSnapshotAfter()).contains("\"descricao\":\"Depois\"");
        }

        @Test
        @DisplayName("sem motivo grava motivo nulo")
        void gravaSemMotivo() {
            when(treinoMapper.toOutputDto(treino)).thenReturn(dto("Depois"));
            when(principalResolver.getCurrentSubject()).thenReturn("coach-sub");

            recorder.record(plano, treino, "{}", null, AGORA);

            ArgumentCaptor<PlannedWorkoutPostApprovalEdit> captor =
                    ArgumentCaptor.forClass(PlannedWorkoutPostApprovalEdit.class);
            verify(repository).save(captor.capture());
            assertThat(captor.getValue().getReason()).isNull();
        }
    }

    private TreinoPlanejadoOutputDto dto(String descricao) {
        return new TreinoPlanejadoOutputDto(
                treino.getId(), null, null, null, TipoTreino.CONTINUO, descricao, null, "60:00", 10.0,
                null, null, null, null, null, null, 55, null, null, false,
                false, null, TreinoExecucaoStatus.PENDENTE, null, null, null, null,
                null, null, false, null);
    }
}
