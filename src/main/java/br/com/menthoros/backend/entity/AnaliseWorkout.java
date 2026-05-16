package br.com.menthoros.backend.entity;

import br.com.menthoros.backend.enums.AnaliseStatus;
import br.com.menthoros.backend.enums.PrimaryAnalysisCause;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "tb_analise_workout",
        indexes = {
                @Index(name = "idx_analise_tenant", columnList = "tenant_id"),
                @Index(name = "idx_analise_treino", columnList = "treino_realizado_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AnaliseWorkout {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "treino_realizado_id", nullable = false, unique = true)
    private UUID treinoRealizadoId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AnaliseStatus status = AnaliseStatus.PENDING;

    @Column(name = "summary_pt", columnDefinition = "TEXT")
    private String summaryPt;

    @Column(name = "technical_interpretation_pt", columnDefinition = "TEXT")
    private String technicalInterpretationPt;

    @Enumerated(EnumType.STRING)
    @Column(name = "primary_cause", length = 50)
    private PrimaryAnalysisCause primaryCause;

    @Column(name = "recommendation_pt", columnDefinition = "TEXT")
    private String recommendationPt;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "tags", columnDefinition = "TEXT[]")
    private List<String> tags;

    @Column(name = "execution_score")
    private Integer executionScore;

    @Column(name = "rationale_pt", columnDefinition = "TEXT")
    private String rationalePt;

    @Column(name = "translation_failed", nullable = false)
    private boolean translationFailed = false;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "analyzed_at")
    private Instant analyzedAt;
}
