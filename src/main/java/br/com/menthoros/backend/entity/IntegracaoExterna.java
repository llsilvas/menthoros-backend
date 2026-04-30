package br.com.menthoros.backend.entity;

import br.com.menthoros.backend.enums.FonteDados;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "tb_integracao_externa",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_integracao_atleta_plataforma",
                columnNames = {"atleta_id", "plataforma"}
        ),
        indexes = {
                @Index(name = "idx_integracao_atleta", columnList = "atleta_id"),
                @Index(name = "idx_integracao_tenant", columnList = "tenant_id"),
                @Index(name = "idx_integracao_external_athlete", columnList = "external_athlete_id, plataforma")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IntegracaoExterna {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36, updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "atleta_id", nullable = false)
    private Atleta atleta;

    @Enumerated(EnumType.STRING)
    @Column(name = "plataforma", nullable = false, length = 50)
    private FonteDados plataforma;

    @Column(name = "external_athlete_id", length = 100)
    private String externalAthleteId;

    @Column(name = "access_token", columnDefinition = "TEXT")
    private String accessToken;

    @Column(name = "refresh_token", columnDefinition = "TEXT")
    private String refreshToken;

    @Column(name = "token_expira_em")
    private Instant tokenExpiraEm;

    @Column(name = "scopes", length = 500)
    private String scopes;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    @Column(name = "ultima_sincronizacao")
    private Instant ultimaSincronizacao;

    @Column(name = "sync_activity_count")
    private Integer syncActivityCount;

    @Column(name = "last_sync_error", length = 500)
    private String lastSyncError;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    @PrePersist
    private void prePersist() {
        this.criadoEm = LocalDateTime.now();
        this.atualizadoEm = LocalDateTime.now();
    }

    @PreUpdate
    private void preUpdate() {
        this.atualizadoEm = LocalDateTime.now();
    }
}
