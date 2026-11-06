package br.com.menthoros.backend.entity;

import br.com.menthoros.backend.enums.WorkoutEditReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Registro append-only de uma edição de treino em plano aprovado (coach-editar-treino-aprovado,
 * D7): o treino antes e depois, quem editou, quando e por quê. Só escrita nesta change — é o sinal
 * de aprendizado, sem tela de leitura. Ids em vez de associações porque ninguém navega daqui.
 */
@Entity
@Table(name = "tb_planned_workout_post_approval_edit")
@Getter
@Setter
@NoArgsConstructor
public class PlannedWorkoutPostApprovalEdit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "planned_workout_id", nullable = false)
    private UUID plannedWorkoutId;

    @Column(name = "weekly_plan_id", nullable = false)
    private UUID weeklyPlanId;

    @Column(name = "edited_by", nullable = false)
    private String editedBy;

    @Column(name = "edited_at", nullable = false)
    private Instant editedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", length = 20)
    private WorkoutEditReason reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot_before", nullable = false, columnDefinition = "jsonb")
    private String snapshotBefore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot_after", nullable = false, columnDefinition = "jsonb")
    private String snapshotAfter;
}
