package br.com.menthoros.backend.entity;

import br.com.menthoros.backend.services.onboarding.OrigemDado;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Trilha append-only de cada recalculo de baseline/score de um atleta (V64,
 * athlete-onboarding-baseline, sessao de grilling 2026-07-21) — uma linha por
 * evento de recalculo, nunca sobrescrita. Existe separada de
 * {@link AthleteBaselineState} (que so guarda o estado atual) para nao perder
 * a evolucao do score durante a calibracao — dado necessario para calibrar as
 * proprias heuristicas hardcoded desta change (duracao da calibracao,
 * threshold do Cenario C) com dado real de producao.
 */
@Entity
@Table(name = "tb_athlete_baseline_history")
@Getter
@Setter
@NoArgsConstructor
public class AthleteBaselineHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "atleta_id", nullable = false)
    private UUID atletaId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "evento", nullable = false, length = 30)
    private String evento;

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

    @PrePersist
    private void prePersist() {
        if (criadoEm == null) {
            criadoEm = Instant.now();
        }
    }
}
