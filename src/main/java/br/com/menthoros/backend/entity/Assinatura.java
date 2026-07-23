package br.com.menthoros.backend.entity;

import br.com.menthoros.backend.enums.StatusAssinatura;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Contrato de cobrança B2B entre uma {@link Assessoria} e a Menthoros no Asaas
 * ({@code tb_assinatura}, V68, assessoria-billing-asaas).
 *
 * <p>1:1 com {@link Assessoria} (via {@code assessoriaId} único, sem {@code @OneToOne} — lookup por
 * repositório para manter o 1:1 sem lazy-loading), sem histórico local (o Asaas é o sistema de
 * registro de cobrança — ver {@code docs/adr/0004}). Uma linha sobrescrita a cada evento.
 *
 * <p>Cross-tenant por natureza: não tem {@code tenant_id} — o job de carência e o webhook operam
 * fora do {@code TenantContext} e resolvem pela {@link Assessoria}/{@code asaasSubscriptionId}.
 *
 * <p>{@code asaasCustomerId}/{@code asaasSubscriptionId} ficam nulos enquanto
 * {@link StatusAssinatura#PENDENTE} (âncora local antes de confirmar o Asaas — design.md Decisão 9).
 */
@Entity
@Table(name = "tb_assinatura")
@Getter
@Setter
@NoArgsConstructor
public class Assinatura {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "assessoria_id", nullable = false, unique = true)
    private UUID assessoriaId;

    @Column(name = "asaas_customer_id", length = 50)
    private String asaasCustomerId;

    @Column(name = "asaas_subscription_id", length = 50, unique = true)
    private String asaasSubscriptionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusAssinatura status;

    @Column(name = "data_proxima_cobranca")
    private Instant dataProximaCobranca;

    @Column(name = "valor")
    private BigDecimal valor;

    /** Instante em que o webhook {@code PAYMENT_OVERDUE} foi processado; usado só pelo job de carência. */
    @Column(name = "overdue_desde")
    private Instant overdueDesde;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em")
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
