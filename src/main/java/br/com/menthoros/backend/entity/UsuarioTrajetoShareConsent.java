package br.com.menthoros.backend.entity;

import br.com.menthoros.backend.enums.TrajetoShareConsentEvento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento <b>append-only</b> do consentimento do atleta para o trajeto GPS completo aparecer nos
 * cards de compartilhamento (LGPD Art. 11, I).
 *
 * <p>Concessão e revogação são linhas; o estado atual é o último evento do usuário no tenant. Nada
 * é atualizado nem apagado — o histórico é a prova de qual versão da Política foi confirmada e de
 * quando o consentimento foi revogado. Por isso a entidade não tem setters.
 */
@Entity
@Table(name = "tb_usuario_trajeto_share_consent")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsuarioTrajetoShareConsent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    /** Tenant do usuário no momento do evento. Sem FK, conforme o Table Design Standard. */
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /** Versão da Política apresentada ao atleta, formato {@code YYYY-MM-DD}. */
    @Column(name = "policy_version", nullable = false, length = 20)
    private String policyVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "evento", nullable = false, length = 10)
    private TrajetoShareConsentEvento evento;

    /** {@code TIMESTAMPTZ} — registro legal não pode ter fuso ambíguo. */
    @Column(name = "registrado_em", nullable = false)
    private Instant registradoEm;

    @PrePersist
    void prePersist() {
        if (registradoEm == null) {
            registradoEm = Instant.now();
        }
    }
}
