package br.com.menthoros.backend.entity;

import br.com.menthoros.backend.enums.FaixaAtletas;
import br.com.menthoros.backend.enums.PerfilWaitlist;
import br.com.menthoros.backend.enums.WaitlistStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Interessado na waitlist pública do Menthoros (pré-signup).
 * Entidade global, sem tenant — não herda {@code BaseEntity}/{@code AuditableEntity}.
 */
@Entity
@Table(name = "tb_waitlist")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class Waitlist {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "nome", nullable = false, length = 120)
    private String nome;

    @Column(name = "email", nullable = false, length = 180)
    private String email;

    @Column(name = "email_normalized", nullable = false, length = 180)
    private String emailNormalized;

    @Column(name = "telefone", length = 20)
    private String telefone;

    @Enumerated(EnumType.STRING)
    @Column(name = "perfil", nullable = false, length = 20)
    private PerfilWaitlist perfil;

    @Enumerated(EnumType.STRING)
    @Column(name = "qtd_atletas", length = 20)
    private FaixaAtletas qtdAtletas;

    @Column(name = "aceite_lgpd", nullable = false)
    private boolean aceiteLgpd;

    @Column(name = "origem", length = 40)
    private String origem;

    @Column(name = "utm_source", length = 255)
    private String utmSource;

    @Column(name = "utm_medium", length = 255)
    private String utmMedium;

    @Column(name = "utm_campaign", length = 255)
    private String utmCampaign;

    @Column(name = "utm_content", length = 255)
    private String utmContent;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "docs_notified_at")
    private Instant docsNotifiedAt;

    @Column(name = "invited_at")
    private Instant invitedAt;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "discarded_at")
    private Instant discardedAt;

    /** Preenchido quando o lead converte em assessoria. Sem FK, mesmo padrão de {@code FoundingInvite.assessoriaId}. */
    @Column(name = "assessoria_id")
    private UUID assessoriaId;

    /**
     * Etapa do lead no funil, derivada dos timestamps — sem coluna própria, mesmo padrão de
     * {@code FoundingInvite} ("o estado é derivado das datas, sem enum"). {@code discardedAt}
     * tem precedência sobre os demais por ser terminal (add-waitlist-status-lifecycle, design D2).
     */
    public WaitlistStatus getStatus() {
        if (discardedAt != null) {
            return WaitlistStatus.DISCARDED;
        }
        if (activatedAt != null) {
            return WaitlistStatus.ACTIVE;
        }
        if (invitedAt != null) {
            return WaitlistStatus.INVITED;
        }
        return WaitlistStatus.NEW;
    }
}
