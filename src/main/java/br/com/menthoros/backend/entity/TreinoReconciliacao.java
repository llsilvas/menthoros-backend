package br.com.menthoros.backend.entity;

import br.com.menthoros.backend.enums.ReconciliationActionType;
import br.com.menthoros.backend.enums.ReconciliationStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Entidade JPA para registro append-only de eventos de reconciliação Strava.
 *
 * Maps tb_treino_reconciliacao table created in V18.
 * This entity logs all reconciliation events between completed activities (TreinoRealizado)
 * and planned workouts (TreinoPlanejado), including automatic scoring and manual coach actions.
 *
 * @author Menthoros Backend
 */
@Entity
@Table(
    name = "tb_treino_reconciliacao",
    indexes = {
        @Index(
            name = "idx_treino_reconciliacao_tenant_realizado",
            columnList = "tenant_id, treino_realizado_id, occurred_at"
        ),
        @Index(
            name = "idx_treino_reconciliacao_actor",
            columnList = "tenant_id, actor_id, occurred_at"
        )
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TreinoReconciliacao {

    /**
     * Primary key: auto-incremented BIGSERIAL in database
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Tenant isolation: customer/assessoria identifier.
     * Not mapped as a JPA field, only as column for multi-tenancy filtering.
     */
    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    /**
     * Reference to the completed activity (TreinoRealizado).
     * FK: tb_treino_realizado(id) with ON DELETE CASCADE.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "treino_realizado_id", nullable = false)
    private TreinoRealizado treinoRealizado;

    /**
     * Type of reconciliation action performed.
     * Values: RECONCILIACAO_AUTOMATICA, VINCULAR_MANUALMENTE, MARCAR_NAO_PLANEJADO, etc.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 50)
    private ReconciliationActionType actionType;

    /**
     * Status before the reconciliation action.
     * Nullable: some actions may not have a "before" state.
     * Values: VINCULADO_AUTOMATICO, AMBIGUO, NAO_PLANEJADO, VINCULADO_MANUAL, PENDENTE
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "before_status", length = 50)
    private ReconciliationStatus beforeStatus;

    /**
     * Status after the reconciliation action.
     * Nullable: some actions may not have an "after" state.
     * Values: VINCULADO_AUTOMATICO, AMBIGUO, NAO_PLANEJADO, VINCULADO_MANUAL, PENDENTE
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "after_status", length = 50)
    private ReconciliationStatus afterStatus;

    /**
     * Planned workout ID before reconciliation.
     * Nullable: may be null if activity was unplanned before.
     * FK reference (logical, not enforced) to tb_treino_planejado.id
     */
    @Column(name = "before_planned_id")
    private Long beforePlannedId;

    /**
     * Planned workout ID after reconciliation.
     * Nullable: may be null if activity marked as unplanned.
     * FK reference (logical, not enforced) to tb_treino_planejado.id
     */
    @Column(name = "after_planned_id")
    private Long afterPlannedId;

    /**
     * Reconciliation compatibility score between 0.0 and 1.0.
     * Represents confidence level of the automatic matching algorithm.
     * Nullable: manual actions may not produce a score.
     * DB Constraint: CHECK (score IS NULL OR (score >= 0 AND score <= 1))
     */
    @Column(name = "score", precision = 3, scale = 2)
    private BigDecimal score;

    /**
     * Reason code for the reconciliation action.
     * Machine-readable identifier explaining why the action was taken.
     * Examples: "LOW_SCORE", "TIME_MISMATCH", "COACH_OVERRIDE"
     */
    @Column(name = "reason_code", length = 100)
    private String reasonCode;

    /**
     * Human-readable explanation of the reconciliation action.
     * Provides additional context or comments about the decision.
     */
    @Column(name = "reason_text", columnDefinition = "TEXT")
    private String reasonText;

    /**
     * Actor identifier: user or system that performed the action.
     * Format examples: "user:uuid", "system:strava-sync", "coach:name"
     */
    @Column(name = "actor_id", nullable = false, length = 255)
    private String actorId;

    /**
     * Timestamp when the reconciliation occurred.
     * Stored as TIMESTAMPTZ in database with DEFAULT NOW().
     * Append-only: used for ordering audit trail.
     */
    @Column(name = "occurred_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private Instant occurredAt;

    /**
     * Helper constructor for creating reconciliation events with core parameters.
     * Useful for service layer when recording reconciliation actions.
     *
     * @param tenantId tenant/customer identifier
     * @param treinoRealizado the completed activity
     * @param actionType the type of action performed
     * @param actorId who/what performed the action
     * @param occurredAt when it occurred
     */
    public TreinoReconciliacao(
        String tenantId,
        TreinoRealizado treinoRealizado,
        ReconciliationActionType actionType,
        String actorId,
        Instant occurredAt
    ) {
        this.tenantId = tenantId;
        this.treinoRealizado = treinoRealizado;
        this.actionType = actionType;
        this.actorId = actorId;
        this.occurredAt = occurredAt;
    }
}
