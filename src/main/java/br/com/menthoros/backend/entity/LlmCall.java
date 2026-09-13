package br.com.menthoros.backend.entity;

import br.com.menthoros.backend.ai.ledger.GenerationOutcome;
import br.com.menthoros.backend.ai.ledger.LlmCallResult;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Chamada LLM — uma linha por chamada lógica ao modelo, em qualquer rota
 * (add-plan-generation-ledger). Observabilidade técnica, não evento de domínio.
 *
 * <p>Sem relação JPA com {@code Atleta} ou {@code PlanoSemanal}: a FK de {@code atleta_id} vive só
 * no banco ({@code ON DELETE SET NULL}) e a ligação com o plano é por
 * {@code generationRequestId}, para que a linha de custo sobreviva à exclusão de ambos.
 */
@Entity
@Table(name = "tb_llm_call")
@Getter
@Setter
@NoArgsConstructor
public class LlmCall {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "atleta_id")
    private UUID atletaId;

    @Column(name = "generation_request_id")
    private UUID generationRequestId;

    @Column(name = "route", nullable = false, length = 20)
    private String route;

    @Column(name = "model", nullable = false, length = 80)
    private String model;

    @Column(name = "input_tokens")
    private Long inputTokens;

    @Column(name = "output_tokens")
    private Long outputTokens;

    @Column(name = "cache_read_tokens")
    private Long cacheReadTokens;

    @Column(name = "cache_write_tokens")
    private Long cacheWriteTokens;

    @Column(name = "cost_usd", precision = 12, scale = 10)
    private BigDecimal costUsd;

    @Column(name = "latency_ms", nullable = false)
    private int latencyMs;

    @Column(name = "attempt")
    private Integer attempt;

    @Column(name = "prompt_version", length = 20)
    private String promptVersion;

    @Column(name = "prompt_hash", length = 64)
    private String promptHash;

    @Column(name = "schema_version", length = 20)
    private String schemaVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 30)
    private LlmCallResult result;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_outcome", length = 30)
    private GenerationOutcome requestOutcome;

    @Column(name = "transport_retries")
    private Integer transportRetries;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "violations", columnDefinition = "jsonb")
    private String violations;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_json", columnDefinition = "jsonb")
    private String responseJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
