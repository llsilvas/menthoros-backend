package br.com.menthoros.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Convite de assessoria fundadora, emitido pelo ADMIN a partir de um inscrito da waitlist.
 *
 * <p>Guarda apenas o <strong>hash</strong> do token. O estado é derivado das datas, sem enum:
 * aberto (nem convertido nem invalidado), expirado, invalidado (reenvio gerou outro) ou convertido
 * (terminal). Ver {@link #isActive(OffsetDateTime)}.</p>
 */
@Entity
@Table(name = "tb_founding_invite")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FoundingInvite {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "waitlist_id", nullable = false)
    private UUID waitlistId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    /** Snapshot do e-mail no momento do convite — a waitlist pode ser editada depois. */
    @Column(name = "email", nullable = false, length = 180)
    private String email;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    /** Nulo quando o SMTP falhou: o registro fica para o reenvio invalidar. */
    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @Column(name = "invalidated_at")
    private OffsetDateTime invalidatedAt;

    @Column(name = "converted_at")
    private OffsetDateTime convertedAt;

    @Column(name = "assessoria_id")
    private UUID assessoriaId;

    /** Subject (JWT) do ADMIN que emitiu o convite. */
    @Column(name = "invited_by", nullable = false, length = 100)
    private String invitedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    /** Aberto = ainda pode virar convertido ou invalidado. Expirado continua "aberto" para o índice. */
    public boolean isOpen() {
        return convertedAt == null && invalidatedAt == null;
    }

    /** Ativo = aberto e dentro da validade: o único estado em que o token abre o cadastro. */
    public boolean isActive(OffsetDateTime now) {
        return isOpen() && expiresAt.isAfter(now);
    }
}
