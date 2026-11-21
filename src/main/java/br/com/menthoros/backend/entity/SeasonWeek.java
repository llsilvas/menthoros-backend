package br.com.menthoros.backend.entity;

import br.com.menthoros.backend.domain.planner.TrainingPhase;
import br.com.menthoros.backend.enums.WeekValueSource;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Semana da temporada: alvos de uma semana do plano de temporada ({@link PlanoTreino}).
 * {@code phase} aceita só BASE, BUILD, PEAK, TAPER e RACE_WEEK (check da V117).
 */
@Entity
@Table(name = "tb_season_week")
@Getter
@Setter
@NoArgsConstructor
public class SeasonWeek {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "season_plan_id", nullable = false)
    private PlanoTreino seasonPlan;

    @Column(name = "week_index", nullable = false)
    private Integer weekIndex;

    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @Enumerated(EnumType.STRING)
    @Column(name = "phase", nullable = false, length = 20)
    private TrainingPhase phase;

    @Column(name = "target_volume_km", precision = 10, scale = 3)
    private BigDecimal targetVolumeKm;

    @Column(name = "target_tss")
    private Double targetTss;

    @Column(name = "long_run_km", precision = 10, scale = 3)
    private BigDecimal longRunKm;

    @Column(name = "deload", nullable = false)
    private boolean deload;

    @Column(name = "key_sessions", columnDefinition = "TEXT")
    private String keySessions;

    @Enumerated(EnumType.STRING)
    @Column(name = "value_source", nullable = false, length = 20)
    private WeekValueSource valueSource;

    @Column(name = "milestone_race_id")
    private UUID milestoneRaceId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
