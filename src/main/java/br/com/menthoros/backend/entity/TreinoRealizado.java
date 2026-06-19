package br.com.menthoros.backend.entity;

import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.ReconciliationStatus;
import br.com.menthoros.backend.enums.StatusSincronizacao;
import br.com.menthoros.backend.enums.TreinoExecucaoStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "tb_treino_realizado",
        indexes = {
                @Index(name = "idx_realizado_atleta_data", columnList = "atleta_id,data_treino"),
                @Index(name = "idx_realizado_data", columnList = "data_treino")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TreinoRealizado extends TreinoBase{

    @Column(name = "tss_calculado")
    private Integer tssCalculado; // TSS real calculado

    @Column(name = "metodo_calculo_tss")
    private String metodoCalculoTss; // "FC", "PACE", "RPE"

    @Column(name = "fc_media")
    private Integer fcMedia;

    @Column(name = "fc_maxima_treino")
    private Integer fcMax;

    @JdbcTypeCode(SqlTypes.INTERVAL_SECOND)
    @Column(name = "pace_media")
    private Duration paceMedia; // min/km

    @Column(name = "velocidade_media")
    private Double velocidadeMedia; // km/h

    @Column(name = "cadencia_media")
    private Integer cadenciaMedia; // passos por minuto

    @Column(name = "potencia_media")
    private Integer potenciaMedia; // watts

    @Column(name = "percepcao_esforco")
    private Integer percepcaoEsforco; // RPE 1-10

    @Column(name = "intensidade_real")
    private Double intensidadeReal; // IF calculado

    // ===== FEEDBACK DO ATLETA =====

    @Column(name = "feedback_atleta", length = 1000)
    private String feedbackAtleta;

    @Column(name = "qualidade_sono_noite_anterior")
    private Integer qualidadeSonoNoiteAnterior; // 1-10

    @Column(name = "nivel_estresse")
    private Integer nivelEstresse; // 1-10

    @Enumerated(EnumType.STRING)
    @Column(name = "fonte_dados")
    private FonteDados fonteDados;

    @Enumerated(EnumType.STRING)
    private TreinoExecucaoStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "atleta_id", nullable = false)
    private Atleta atleta;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "treino_planejado_id")
    private TreinoPlanejado treinoPlanejado;

    @ManyToOne(fetch = FetchType.LAZY)
    private PlanoSemanal planoSemanal;

    @OneToMany(mappedBy = "treinoRealizado", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("ordem ASC")
    private List<EtapaRealizada> etapasRealizadas = new ArrayList<>();

    @Column(name = "external_id")
    private String externalId;

    // ===== CAMPOS STRAVA (V13) =====

    @Enumerated(EnumType.STRING)
    @Column(name = "status_sincronizacao", length = 50)
    private StatusSincronizacao statusSincronizacao;

    @Column(name = "sincronizado_em")
    private Instant sincronizadoEm;

    @Column(name = "url_externo", length = 500)
    private String urlExterno;

    @Column(name = "metadados_sincronizacao", columnDefinition = "TEXT")
    private String metadadosSincronizacao;

    @Column(name = "elapsed_time_seg")
    private Integer elapsedTimeSeg;

    @Column(name = "suffer_score")
    private Integer sufferScore;

    @Column(name = "device_name", length = 200)
    private String deviceName;

    @Column(name = "gear_name", length = 200)
    private String gearName;

    // ===== CAMPOS RECONCILIAÇÃO (V17) =====

    @Enumerated(EnumType.STRING)
    @Column(name = "reconciliation_status")
    private ReconciliationStatus reconciliationStatus;

    @Column(name = "treino_planejado_id", insertable = false, updatable = false)
    private UUID treinoPlanejadoId;

    @Column(name = "reconciliation_score", precision = 3, scale = 2)
    private BigDecimal reconciliationScore;

    @Column(name = "reconciliation_reason_code")
    private String reconciliationReasonCode;

    @Column(name = "reconciled_at")
    private Instant reconciledAt;

    @Column(name = "reconciled_by")
    private String reconciledBy;

    /**
     * Verifica se treino foi mais difícil que esperado
     */
    public boolean foiMaisDificilQueEsperado() {
        if (treinoPlanejado == null ||
                treinoPlanejado.getPercepcaoEsforcoEsperada() == null ||
                percepcaoEsforco == null) {
            return false;
        }

        return percepcaoEsforco > treinoPlanejado.getPercepcaoEsforcoEsperada() + 1;
    }

    /**
     * Calcula diferença entre TSS planejado vs realizado
     */
    public Integer getDiferencaTss() {
        if (treinoPlanejado == null ||
                treinoPlanejado.getTssPlanejado() == null ||
                tssCalculado == null) {
            return null;
        }

        return tssCalculado - treinoPlanejado.getTssPlanejado();
    }

    @PrePersist
    private void prePersist() {
        if (this.getTenantId() == null && this.planoSemanal != null && this.planoSemanal.getAssessoria() != null) {
            this.setTenantId(this.planoSemanal.getAssessoria().getId());
        }
        if (this.getTenantId() == null && this.atleta != null && this.atleta.getAssessoria() != null) {
            this.setTenantId(this.atleta.getAssessoria().getId());
        }
        if(this.getCriadoEm() == null) {
            this.setCriadoEm(LocalDateTime.now());
        }
        this.setAtualizadoEm(LocalDateTime.now());
    }

    @PreUpdate
    private void preUpdate() {
        this.setAtualizadoEm(LocalDateTime.now());
    }

}

