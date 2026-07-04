package br.com.menthoros.backend.entity;

import br.com.menthoros.backend.enums.MotivoKudos;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "tb_kudos",
        indexes = {
                @Index(name = "idx_kudos_tenant_atleta", columnList = "tenant_id, atleta_id, created_at")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class Kudos {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "atleta_id", nullable = false)
    private Atleta atleta;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coach_id", nullable = false)
    private Usuario coach;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MotivoKudos motivo;

    @Column(nullable = false)
    private LocalDate data;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    private void aoCriar() {
        createdAt = Instant.now();
        if (data == null) {
            data = LocalDate.now();
        }
    }
}
