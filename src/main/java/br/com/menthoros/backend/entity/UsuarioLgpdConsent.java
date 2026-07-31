package br.com.menthoros.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Registro <b>append-only</b> do consentimento LGPD de um {@link Usuario}: uma linha por aceite,
 * versionada pela data de vigência da Política de Privacidade e dos Termos de Uso.
 *
 * <p>Nada é sobrescrito. Quando a Política muda, o novo aceite cria uma linha nova e a anterior
 * permanece intacta — é isso que sustenta a prova de <b>qual texto</b> o coach aceitou e quando,
 * inclusive para versões já superadas.
 *
 * <p>Não existe flag equivalente em {@code tb_usuario}: "está consentido?" é derivado da existência
 * de linha com as versões vigentes. Persistir o flag também na outra tabela seria estado redundante,
 * capaz de divergir deste registro.
 *
 * <p>A constraint {@code uk_usuario_lgpd_consent_versoes} não é só integridade: é ela que torna o
 * insert idempotente e arbitra a corrida de dois aceites simultâneos, dispensando update condicional
 * na aplicação.
 *
 * <p><b>Esta entidade nunca sofre update nem delete</b> — o repositório correspondente não expõe
 * essas operações por contrato.
 */
@Entity
@Table(name = "tb_usuario_lgpd_consent")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class UsuarioLgpdConsent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    /**
     * Tenant (assessoria) do usuário no momento do aceite. Sem FK, conforme o Table Design Standard
     * — a integridade é da aplicação, então o service valida que bate com o tenant do usuário antes
     * de gravar.
     */
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /** Data de vigência da Política de Privacidade aceita, no formato {@code YYYY-MM-DD}. */
    @Column(name = "policy_version", nullable = false, length = 20)
    private String policyVersion;

    /** Data de vigência dos Termos de Uso aceitos, no formato {@code YYYY-MM-DD}. */
    @Column(name = "terms_version", nullable = false, length = 20)
    private String termsVersion;

    /** Momento do aceite. {@code TIMESTAMPTZ} — registro legal não pode ter fuso ambíguo. */
    @Column(name = "consented_at", nullable = false)
    private Instant consentedAt;

    @PrePersist
    void prePersist() {
        if (consentedAt == null) {
            consentedAt = Instant.now();
        }
    }
}
