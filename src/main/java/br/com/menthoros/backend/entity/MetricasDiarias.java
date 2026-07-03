package br.com.menthoros.backend.entity;


import br.com.menthoros.backend.enums.NivelProntidao;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "tb_metricas_diarias",
        indexes = {
                @Index(name = "idx_atleta_data", columnList = "atleta_id, data", unique = true),
                @Index(name = "idx_atleta_data_desc", columnList = "atleta_id, data DESC")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class MetricasDiarias {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "atleta_id", nullable = false)
    private Atleta atleta;

    @Column(nullable = false)
    private LocalDate data;

    @Column(nullable = false)
    private Integer tss = 0;

    @Column(nullable = false)
    private Double ctl = 0.0;

    @Column(nullable = false)
    private Double atl = 0.0;

    @Column(nullable = false)
    private Double tsb = 0.0;

    @Column(name = "ramp_rate")
    private Double rampRate = 0.0;

    @Column(name = "fatigue_ratio")
    private Double fatigueRatio = 0.0;

    @Column(name = "forma_percentual")
    private Double formaPercentual; // (TSB/CTL) * 100

    // ===== CONTEXTO DO DIA =====

    @Column(name = "treinos_realizados")
    private Integer treinosRealizados = 0;

    @Column(name = "volume_km", precision = 6, scale = 2)
    private BigDecimal volumeKm;

    @Column(name = "foi_dia_descanso")
    private Boolean foiDiaDescanso = false;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    // ===== SEMÂNTICA TSB: INÍCIO E FIM DE DIA =====
    // tsbInicioDia = CTL_ontem - ATL_ontem (prontidão pré-treino)
    // tsbFimDia    = CTL_hoje  - ATL_hoje  (estado pós-carga)

    @Column(name = "ctl_inicio_dia")
    private Double ctlInicioDia;

    @Column(name = "atl_inicio_dia")
    private Double atlInicioDia;

    @Column(name = "tsb_inicio_dia")
    private Double tsbInicioDia;

    @Column(name = "ctl_fim_dia")
    private Double ctlFimDia;

    @Column(name = "atl_fim_dia")
    private Double atlFimDia;

    @Column(name = "tsb_fim_dia")
    private Double tsbFimDia;

    // ===== READINESS (checkin diário de prontidão subjetiva) =====

    @Column(name = "readiness_score", precision = 4, scale = 3)
    private BigDecimal readinessScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "nivel_prontidao")
    private NivelProntidao nivelProntidao;

    @PrePersist
    @PreUpdate
    private void calcularDerivativos() {
        if (ctl != null && ctl > 0) {
            fatigueRatio = atl / ctl;
            formaPercentual = (tsb / ctl) * 100;
        }

        foiDiaDescanso = (tss == 0 || tss < 10);
    }

}
