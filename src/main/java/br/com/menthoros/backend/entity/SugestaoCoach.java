package br.com.menthoros.backend.entity;

import br.com.menthoros.backend.enums.StatusSugestao;
import br.com.menthoros.backend.enums.TipoSugestao;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Sugestão gerada pelo job diário para revisão do treinador.
 *
 * <p>Ciclo de vida: PENDING → APPROVED | REJECTED.
 * Idempotência garantida pelo índice {@code uk_sugestao_pending} (UNIQUE parcial no PostgreSQL).
 */
@Entity
@Table(name = "tb_sugestao_coach", indexes = {
        @Index(name = "idx_sugestao_coach_atleta", columnList = "atleta_id"),
        @Index(name = "idx_sugestao_coach_tenant_status", columnList = "tenant_id, status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SugestaoCoach {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atleta_id", nullable = false)
    private Atleta atleta;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoSugestao tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusSugestao status;

    /** Confiança da sugestão: HIGH | MEDIUM | LOW — mapeado de Severidade do sinal de origem. */
    @Column(name = "confidence", nullable = false, length = 10)
    private String confidence;

    /** Ação sugerida — cópia de suggestedAction do CoachAttentionItemOutputDto. */
    @Column(name = "summary", nullable = false, columnDefinition = "TEXT")
    private String summary;

    /** RecommendationExplanation serializado como JSONB; nullable quando o sinal não tem explanation. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reasoning", columnDefinition = "jsonb")
    private String reasoningJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;
}
