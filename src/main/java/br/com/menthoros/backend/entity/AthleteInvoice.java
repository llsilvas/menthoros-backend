package br.com.menthoros.backend.entity;

import br.com.menthoros.backend.domain.audit.AuditableEntity;
import br.com.menthoros.backend.enums.InvoiceStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Mensalidade gerada por um {@link AthleteContract} (glossário: "Mensalidade"). O par
 * ({@code paidAt}, {@code paidAmount}) é o registro da baixa; não há tabela de evento.
 * {@code UNIQUE (contract_id, due_date)} é a rede contra geração duplicada.
 */
@Entity
@Table(name = "tb_athlete_invoice")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AthleteInvoice extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "contract_id", nullable = false)
    private UUID contractId;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    /** Cópia do valor do contrato no momento da geração; editar o contrato não a altera. */
    @Column(name = "amount", precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InvoiceStatus status;

    @Column(name = "paid_at")
    private LocalDate paidAt;

    @Column(name = "paid_amount", precision = 10, scale = 2)
    private BigDecimal paidAmount;

    /** Derivado, nunca persistido: em aberto com vencimento antes de {@code today}. */
    public boolean isOverdue(LocalDate today) {
        return status == InvoiceStatus.OPEN && dueDate.isBefore(today);
    }
}
