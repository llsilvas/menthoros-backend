package br.com.menthoros.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Registro persistido de uma execução de skill de domínio.
 *
 * <p>Cada vez que um {@code DomainSkill} é executado pelo {@code SkillOrchestratorService},
 * um registro desta entidade é criado com o resultado serializado (payload, evidências,
 * recomendações) para rastreabilidade, auditoria e análise histórica.</p>
 *
 * <p>Design:</p>
 * <ul>
 *   <li>Append-only: registros não são atualizados após criação.</li>
 *   <li>Soft-FK: {@code atleta_id} usa {@code ON DELETE SET NULL} — registros sobrevivem à exclusão do atleta.</li>
 *   <li>Referências {@code planoSemanalId} e {@code treinoRealizadoId} são soft (sem FK constraint).</li>
 *   <li>Campos JSON (payload, evidence, recommendations) armazenados como JSONB no PostgreSQL.</li>
 * </ul>
 */
@Entity
@Table(name = "tb_skill_execution", indexes = {
        @Index(name = "idx_skill_exec_atleta", columnList = "atleta_id, executed_at DESC"),
        @Index(name = "idx_skill_exec_key", columnList = "skill_key, skill_version"),
        @Index(name = "idx_skill_exec_tenant", columnList = "tenant_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SkillExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Identificador único da skill (ex: "recovery-load-v1"). */
    @Column(name = "skill_key", nullable = false, length = 100)
    private String skillKey;

    /** Versão da skill (ex: "1.0"). */
    @Column(name = "skill_version", nullable = false, length = 20)
    private String skillVersion;

    /** Categoria funcional da skill, armazenada como string do enum {@code SkillCategory}. */
    @Column(name = "skill_category", length = 50)
    private String skillCategory;

    /** Severidade do resultado: INFO, WARNING, CRITICAL ou BLOCKER. */
    @Column(name = "severity", nullable = false, length = 20)
    private String severity;

    /** Confiança no resultado: LOW, MEDIUM ou HIGH. */
    @Column(name = "confidence", nullable = false, length = 20)
    private String confidence;

    /** Payload serializado do resultado da skill (JSONB). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", columnDefinition = "jsonb")
    private String payloadJson;

    /** Evidências que embasam o resultado, serializadas como array JSON (JSONB). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_json", columnDefinition = "jsonb")
    private String evidenceJson;

    /** Recomendações de ação, serializadas como array JSON (JSONB). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recommendations_json", columnDefinition = "jsonb")
    private String recommendationsJson;

    /** Tenant isolamento multi-tenant — obrigatório. */
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /**
     * Atleta analisado.
     * Referência soft: {@code ON DELETE SET NULL} — registro sobrevive à exclusão do atleta.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atleta_id")
    private Atleta atleta;

    /** Referência opcional ao plano semanal associado (soft, sem FK constraint). */
    @Column(name = "plano_semanal_id")
    private UUID planoSemanalId;

    /** Referência opcional ao treino realizado associado (soft, sem FK constraint). */
    @Column(name = "treino_realizado_id")
    private UUID treinoRealizadoId;

    /** Momento da execução da skill. */
    @Column(name = "executed_at", nullable = false)
    private Instant executedAt;

    /** Data de referência usada nos cálculos da skill (geralmente a data atual do contexto). */
    @Column(name = "data_referencia")
    private LocalDate dataReferencia;
}
