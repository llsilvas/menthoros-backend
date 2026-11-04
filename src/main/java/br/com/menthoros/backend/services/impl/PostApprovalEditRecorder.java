package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.entity.PlannedWorkoutPostApprovalEdit;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.enums.WorkoutEditReason;
import br.com.menthoros.backend.mapper.TreinoMapper;
import br.com.menthoros.backend.repository.PlannedWorkoutPostApprovalEditRepository;
import br.com.menthoros.backend.security.AuthenticatedPrincipalResolver;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Grava o registro da edição de treino em plano aprovado (coach-editar-treino-aprovado, D7): o
 * {@code TreinoPlanejadoOutputDto} antes e depois, quem editou, quando e o motivo opcional.
 *
 * <p>O JSON e o motivo podem ser dado de saúde (LGPD): nada daqui vai para log ou mensagem de erro.
 */
@Component
@RequiredArgsConstructor
class PostApprovalEditRecorder {

    private final TreinoMapper treinoMapper;
    private final ObjectMapper objectMapper;
    private final AuthenticatedPrincipalResolver principalResolver;
    private final PlannedWorkoutPostApprovalEditRepository repository;

    /** <b>Idempotente:</b> YES. <b>Side Effects:</b> NONE. */
    String snapshot(TreinoPlanejado treino) {
        try {
            return objectMapper.writeValueAsString(treinoMapper.toOutputDto(treino));
        } catch (JsonProcessingException e) {
            // sem a causa na mensagem: ela pode carregar trecho do treino
            throw new IllegalStateException("Falha ao serializar o snapshot do treino " + treino.getId());
        }
    }

    /**
     * <b>Idempotente:</b> NO — uma linha nova por chamada. <b>Side Effects:</b> Database insert.
     * <b>Tenant-aware:</b> YES — grava o tenant do treino.
     */
    void record(PlanoSemanal plano, TreinoPlanejado treino, String snapshotBefore,
                @Nullable WorkoutEditReason reason, Instant editedAt) {
        PlannedWorkoutPostApprovalEdit edit = new PlannedWorkoutPostApprovalEdit();
        edit.setTenantId(treino.getTenantId());
        edit.setPlannedWorkoutId(treino.getId());
        edit.setWeeklyPlanId(plano.getId());
        edit.setEditedBy(principalResolver.getCurrentSubject());
        edit.setEditedAt(editedAt);
        edit.setReason(reason);
        edit.setSnapshotBefore(snapshotBefore);
        edit.setSnapshotAfter(snapshot(treino));
        repository.save(edit);
    }
}
