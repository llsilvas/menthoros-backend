package br.com.menthoros.backend.entity;

import br.com.menthoros.backend.enums.PlanoStatus;
import br.com.menthoros.backend.enums.SeasonPlanStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "tb_plano_treino")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PlanoTreino {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36, updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atleta_id", nullable = false)
    private Atleta atleta;

    // ManyToOne: cada versão da temporada é um PlanoTreino apontando para a mesma prova-alvo.
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "prova_id", nullable = false)
    private Prova prova;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Assessoria assessoria;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false)
    private String descricao;

    @Column(name = "data_inicio", nullable = false)
    private LocalDate dataInicio;

    @Column(nullable = false)
    private String objetivo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PlanoStatus status;

    // Sem REMOVE e sem orphanRemoval: apagar a temporada ou desvincular não pode levar plano semanal
    // aprovado junto; a FK (V116) põe plano_treino_id em NULL.
    @OneToMany(mappedBy = "planoTreino", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    private List<PlanoSemanal> planoSemanalList;

    @OneToOne(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinColumn(name = "contexto_id", referencedColumnName = "id")
    private PlanoMetaDados planoMetaDados;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prova_alvo_id")
    private Prova provaAlvo; // opcional: referência à prova alvo cadastrada

    // Plano de temporada (V116). Tudo nulo em PlanoTreino legado; o status legado acima não é lido pela temporada.

    @Enumerated(EnumType.STRING)
    @Column(name = "season_status", length = 20)
    private SeasonPlanStatus seasonStatus;

    @Column(name = "total_weeks")
    private Integer totalWeeks;

    @Column(name = "version")
    private Integer version;

    @Column(name = "version_reason")
    private String versionReason;

    @Column(name = "initial_ctl")
    private Double initialCtl;

    /** Volume semanal de partida do motor, em km; nulo nas temporadas anteriores à V121. */
    @Column(name = "initial_volume_km", precision = 6, scale = 1)
    private BigDecimal initialVolumeKm;

    @Column(name = "peak_volume_km", precision = 10, scale = 3)
    private BigDecimal peakVolumeKm;

    @Column(name = "max_long_run_km", precision = 10, scale = 3)
    private BigDecimal maxLongRunKm;

    @Column(name = "short_preparation")
    private Boolean shortPreparation;

    /** Usuario.id do treinador que aprovou. */
    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    /** Temporada ACTIVE da qual este rascunho de nova versão foi copiado; nulo na v1 (add-season-plan-adjust-v2). */
    @Column(name = "base_season_plan_id")
    private UUID baseSeasonPlanId;
}
