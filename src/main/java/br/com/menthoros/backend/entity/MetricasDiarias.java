package br.com.menthoros.backend.entity;


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
