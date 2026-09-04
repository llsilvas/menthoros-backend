package br.com.menthoros.backend.entity;

import br.com.menthoros.backend.enums.ConsumedReviewOutcome;
import br.com.menthoros.backend.enums.MotivoReaberturaRevisao;
import br.com.menthoros.backend.enums.OrigemAprovacao;
import br.com.menthoros.backend.enums.OrigemEncerramento;
import br.com.menthoros.backend.enums.PlanoReviewStatus;
import br.com.menthoros.backend.enums.PlanoStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "tb_plano_semanal")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class PlanoSemanal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "atleta_id", nullable = false)
    private Atleta atleta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plano_treino_id")
    private PlanoTreino planoTreino; // Opcional: plano de longo prazo (ex: para prova alvo)

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "plano_metadados_id", nullable = false)
    private PlanoMetaDados planoMetaDados; // Preferências do atleta + snapshot da semana

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Assessoria assessoria;

    @Column(name = "semana_inicio", nullable = false)
    private LocalDate semanaInicio;

    @Column(name = "semana_fim", nullable = false)
    private LocalDate semanaFim;

    @Column(name = "volume_planejado_km", nullable = false, precision = 10, scale = 3)
    private BigDecimal volumePlanejadoKm;

    @Column(name = "volume_realizado_km", precision = 10, scale = 3)
    private BigDecimal volumeRealizadoKm; // pode começar como null e ser preenchido depois

    @Column(name = "volume_alvo_km", precision = 10, scale = 3)
    private BigDecimal volumeAlvoKm;

    @Column(name = "tsb_inicio", precision = 10, scale = 3)
    private BigDecimal tsbInicio;

    @Column(name = "tsb_fim", precision = 10, scale = 3)
    private BigDecimal tsbFim;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PlanoStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false)
    private PlanoReviewStatus reviewStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "origem_encerramento", length = 15)
    private OrigemEncerramento origemEncerramento;

    @Column(name = "review_comment", columnDefinition = "TEXT")
    private String reviewComment;

    @Enumerated(EnumType.STRING)
    @Column(name = "origem_aprovacao", length = 30)
    private OrigemAprovacao origemAprovacao;

    // --- Revisão semanal consumida como insumo (add-weekly-review-llm-focus) ---
    // Gravados na geração; o desfecho é resolvido na aprovação/rejeição.
    // Ficam no plano (e não na revisão) porque uma revisão é 1:N com os planos que a consomem.

    /** Revisão usada como insumo na geração deste plano; nulo quando nenhuma foi consumida. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consumed_review_id")
    private RevisaoSemanal consumedReview;

    /** O que este plano fez com a revisão consumida — proxy agregado de adoção, não julgamento individual. */
    @Enumerated(EnumType.STRING)
    @Column(name = "consumed_review_outcome", length = 20)
    private ConsumedReviewOutcome consumedReviewOutcome;

    // --- Reabertura de revisão por prova (prova-no-plano-semanal, D4) ---
    // A prova entrou/saiu de uma semana já aprovada: o plano volta a AGUARDANDO_REVISAO em vez
    // de ficar sinalizado por fora. aprovarTransicao/rejeitar limpam os dois campos.

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo_reabertura", length = 20)
    private MotivoReaberturaRevisao motivoReabertura;

    @Column(name = "reaberto_em")
    private LocalDateTime reabertoEm;

    @Column(name = "objetivo_semana")
    private String objetivoSemanal;

    @Column(columnDefinition = "TEXT")
    private String observacoes;

    @OneToMany(mappedBy = "planoSemanal", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TreinoPlanejado> treinosPlanejados;

    @Version
    @Column(name = "versao")
    private Long versao;

    // --- Auditoria PlannerEngine (shadow mode, deterministic-planner-engine) ---
    // Populado apenas quando planner-engine.shadow=true; null em modo legado puro.
    // Nao exposto ao atleta por default (design.md Decisao 9).

    @Column(name = "planner_enabled")
    private Boolean plannerEnabled;

    @Column(name = "planner_version", length = 20)
    private String plannerVersion;

    @Column(name = "planner_phase", length = 30)
    private String plannerPhase;

    @Column(name = "planner_requires_coach_review")
    private Boolean plannerRequiresCoachReview;

    @Column(name = "planner_skeleton_hash", length = 64)
    private String plannerSkeletonHash;

    @Column(name = "planner_compliance_status", length = 30)
    private String plannerComplianceStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "planner_metadata_json", columnDefinition = "jsonb")
    private String plannerMetadataJson;

}
