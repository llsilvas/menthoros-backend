package br.com.menthoros.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Snapshot imutável de uma projeção de tempo de prova.
 * Append-only: nenhum campo de negócio é atualizado após criação,
 * exceto is_official (troca de oficial) e coach_reviewed_at (revisão do coach).
 */
@Entity
@Table(name = "tb_race_projection_snapshot")
@Getter
@Setter
@NoArgsConstructor
public class RaceProjectionSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "athlete_id", nullable = false)
    private Atleta athlete;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "race_id")
    private Prova race;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt = Instant.now();

    @Column(name = "weeks_to_race_at_generation")
    private Integer weeksToRaceAtGeneration;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "projections_json", nullable = false, columnDefinition = "jsonb")
    private String projectionsJson;

    @Column(name = "confidence", nullable = false, length = 10)
    private String confidence;

    @Column(name = "is_official", nullable = false)
    private boolean official = false;

    @Column(name = "coach_id", nullable = false)
    private UUID coachId;

    @Column(name = "coach_reviewed_at")
    private Instant coachReviewedAt;

    @Column(name = "ctl_at_generation", precision = 6, scale = 2)
    private BigDecimal ctlAtGeneration;

    @Column(name = "tsb_at_generation", precision = 6, scale = 2)
    private BigDecimal tsbAtGeneration;

    @Column(name = "regression_r_squared", precision = 5, scale = 4)
    private BigDecimal regressionRSquared;

    @Column(name = "riegel_exponent_used", precision = 6, scale = 4)
    private BigDecimal riegelExponentUsed;

    @Column(name = "riegel_calibrated", nullable = false)
    private boolean riegelCalibrated = false;

    @Column(name = "training_weeks_used")
    private Integer trainingWeeksUsed;

    @Column(name = "model_used", length = 100)
    private String modelUsed;
}
