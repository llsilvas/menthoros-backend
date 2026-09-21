package br.com.menthoros.backend.entity;

import br.com.menthoros.backend.domain.audit.AuditableEntity;
import br.com.menthoros.backend.enums.ContractPeriodicity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Contrato comercial entre o atleta e a assessoria (glossário: "Contrato do atleta"). Um ativo
 * por atleta, garantido pelo índice parcial {@code uq_athlete_contract_active}; encerrados
 * coexistem. Gera {@link AthleteInvoice} enquanto ativo; editar vale só para as futuras.
 *
 * <p>{@code tenantId} solto e filtro manual nas queries, como {@link AthleteInvite}. O
 * {@code @Version} é a rede para a corrida scheduler × proprietário; o lock pessimista do
 * repositório é a serialização de fato.</p>
 */
@Entity
@Table(name = "tb_athlete_contract")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AthleteContract extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "athlete_id", nullable = false)
    private UUID athleteId;

    @Enumerated(EnumType.STRING)
    @Column(name = "periodicity", nullable = false, length = 20)
    private ContractPeriodicity periodicity;

    /** Nulo só no contrato migrado do modelo legado; a UI exige valor. */
    @Column(name = "amount", precision = 10, scale = 2)
    private BigDecimal amount;

    /** 1–31; meses mais curtos usam o último dia (ver {@code DueDateCalendar}). */
    @Column(name = "due_day", nullable = false)
    private int dueDay;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** Nulo = contrato ativo. Encerrar não mexe nas mensalidades em aberto. */
    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    /** Consumido por add-aviso-mensalidade (e-mail ao atleta). */
    @Builder.Default
    @Column(name = "athlete_notice_enabled", nullable = false)
    private boolean athleteNoticeEnabled = true;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public boolean isActive() {
        return endedAt == null;
    }
}
