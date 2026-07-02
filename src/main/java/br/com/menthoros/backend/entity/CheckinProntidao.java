package br.com.menthoros.backend.entity;

import br.com.menthoros.backend.enums.NivelProntidao;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "tb_checkin_prontidao",
        indexes = {
                @Index(name = "idx_checkin_prontidao_atleta_data", columnList = "atleta_id, data", unique = true),
                @Index(name = "idx_checkin_prontidao_tenant", columnList = "tenant_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class CheckinProntidao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "atleta_id", nullable = false)
    private Atleta atleta;

    @Column(nullable = false)
    private LocalDate data;

    @Column(name = "qualidade_sono", nullable = false)
    private Integer qualidadeSono;

    @Column(nullable = false)
    private Integer humor;

    @Column(name = "dores_musculares", nullable = false)
    private Integer doresMusculares;

    @Column(name = "nivel_energia", nullable = false)
    private Integer nivelEnergia;

    @Column(nullable = false)
    private Integer estresse;

    @Column(columnDefinition = "TEXT")
    private String observacoes;

    @Column(name = "readiness_score", nullable = false, precision = 4, scale = 3)
    private BigDecimal readinessScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "nivel_prontidao", nullable = false)
    private NivelProntidao nivelProntidao;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    private void aoCriar() {
        Instant agora = Instant.now();
        createdAt = agora;
        updatedAt = agora;
    }

    @PreUpdate
    private void aoAtualizar() {
        updatedAt = Instant.now();
    }
}
