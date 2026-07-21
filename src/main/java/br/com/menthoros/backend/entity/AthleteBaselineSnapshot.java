package br.com.menthoros.backend.entity;

import br.com.menthoros.backend.services.onboarding.OrigemDado;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Persiste o baseline calculado no onboarding (design.md Decisao 6/10,
 * athlete-onboarding-baseline, V59) — CTL/ATL/TSB + flags ESTIMATED/MEASURED
 * por componente + score/tier de confianca. Uma linha por atleta.
 *
 * <p>NAO e o mesmo tipo do record {@code AthleteBaseline} (contrato minimo
 * de leitura reservado por {@code deterministic-planner-engine}, 2 campos)
 * — esta entidade e o lado de escrita/persistencia completo; o record e
 * mapeado a partir dela na borda do {@code OnboardingContext}.
 */
@Entity
@Table(name = "tb_athlete_baseline_snapshot")
@Getter
@Setter
@NoArgsConstructor
public class AthleteBaselineSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atleta_id", nullable = false)
    private Atleta atleta;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "ctl_estimado")
    private Double ctlEstimado;

    @Column(name = "atl_estimado")
    private Double atlEstimado;

    @Column(name = "tsb_estimado")
    private Double tsbEstimado;

    @Column(name = "ctl_flag")
    @Enumerated(EnumType.STRING)
    private OrigemDado ctlFlag;

    @Column(name = "atl_flag")
    @Enumerated(EnumType.STRING)
    private OrigemDado atlFlag;

    @Column(name = "tsb_flag")
    @Enumerated(EnumType.STRING)
    private OrigemDado tsbFlag;

    @Column(name = "confidence_score")
    private Integer confidenceScore;

    @Column(name = "confidence_tier", length = 1)
    private String confidenceTier;

    @Column(name = "calculated_at", nullable = false)
    private Instant calculatedAt;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    @PrePersist
    private void prePersist() {
        Instant agora = Instant.now();
        if (criadoEm == null) {
            criadoEm = agora;
        }
        atualizadoEm = agora;
    }

    @PreUpdate
    private void preUpdate() {
        atualizadoEm = Instant.now();
    }
}
