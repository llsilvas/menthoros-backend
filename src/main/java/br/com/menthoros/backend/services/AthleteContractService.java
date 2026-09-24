package br.com.menthoros.backend.services;

import br.com.menthoros.backend.domain.billing.AthleteBilling;
import br.com.menthoros.backend.dto.input.AthleteContractInputDto;
import br.com.menthoros.backend.entity.AthleteContract;
import br.com.menthoros.backend.entity.AthleteInvoice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Único dono das regras do contrato do atleta e das mensalidades (design D2). Controller e
 * scheduler só chamam. Cross-tenant e inexistente são indistinguíveis: {@code DomainNotFoundException}
 * (design D9). Transição de estado inválida: {@code DomainConflictException} (design D6).
 */
public interface AthleteContractService {

    /** Contrato ativo do atleta no tenant corrente, se houver. Idempotent: YES. Tenant-aware: YES. */
    Optional<AthleteContract> findActiveContract(UUID athleteId);

    /** Mensalidades do contrato, mais recente primeiro. Idempotent: YES. Tenant-aware: YES. */
    List<AthleteInvoice> listInvoices(UUID contractId);

    /**
     * Cria o contrato ativo do atleta (e a primeira mensalidade) ou edita o existente; edição não
     * toca mensalidades já geradas. Idempotent: YES para o mesmo input. Tenant-aware: YES.
     */
    AthleteContract createOrUpdate(UUID athleteId, AthleteContractInputDto input);

    /** Encerra o contrato ativo; mensalidades em aberto ficam como estão. Idempotent: NO. Tenant-aware: YES. */
    AthleteContract end(UUID athleteId);

    /**
     * Garante que o contrato ativo tem uma mensalidade com vencimento maior ou igual a hoje,
     * gerando as faltantes em sequência (design D3). Chamado na criação e pelo scheduler.
     *
     * @return quantas mensalidades foram geradas nesta chamada
     */
    int ensureNextInvoice(UUID contractId, UUID tenantId, LocalDate today);

    /** OPEN → PAID. Idempotent: NO (segunda chamada é 409). Tenant-aware: YES. */
    AthleteInvoice markPaid(UUID invoiceId, LocalDate paidAt, BigDecimal paidAmount);

    /** PAID → OPEN, limpando data e valor pagos. Idempotent: NO. Tenant-aware: YES. */
    AthleteInvoice undoPayment(UUID invoiceId);

    /** OPEN → CANCELLED ("este período não cobra"). Idempotent: NO. Tenant-aware: YES. */
    AthleteInvoice cancel(UUID invoiceId);

    /** Status e próximo vencimento de um atleta; vazio sem contrato ativo nem mensalidade em aberto. */
    Optional<AthleteBilling> resolveBilling(UUID athleteId, LocalDate today);

    /** Versão em lote para o roster, sem N+1 (design D4). Atletas sem cobrança ficam fora do mapa. */
    Map<UUID, AthleteBilling> resolveBilling(Collection<UUID> athleteIds, LocalDate today);
}
