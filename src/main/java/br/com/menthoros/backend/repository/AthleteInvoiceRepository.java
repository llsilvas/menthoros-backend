package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.entity.AthleteInvoice;
import br.com.menthoros.backend.repository.projection.AthleteOpenInvoiceView;
import br.com.menthoros.backend.repository.projection.ContractLastDueDateView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Toda query recebe {@code tenantId} explícito — o filtro de tenant é manual neste projeto. */
@Repository
public interface AthleteInvoiceRepository extends JpaRepository<AthleteInvoice, UUID> {

    /** Tenant-aware: YES. Ausência e cross-tenant são indistinguíveis (404 no serviço). */
    Optional<AthleteInvoice> findByIdAndTenantId(UUID id, UUID tenantId);

    /** Tenant-aware: YES. */
    List<AthleteInvoice> findByContractIdAndTenantIdOrderByDueDateDesc(UUID contractId, UUID tenantId);

    /** Tenant-aware: YES. A "última" mensalidade, base da próxima geração. */
    Optional<AthleteInvoice> findTopByContractIdAndTenantIdOrderByDueDateDesc(UUID contractId, UUID tenantId);

    /**
     * Mensalidades OPEN de um lote de atletas, com contrato ativo ou encerrado (em aberto conta
     * sempre — CA10). Uma query para o roster inteiro. Tenant-aware: YES.
     */
    @Query("""
            SELECT c.athleteId AS athleteId, i.dueDate AS dueDate
            FROM AthleteInvoice i, AthleteContract c
            WHERE i.contractId = c.id
              AND i.tenantId = :tenantId
              AND c.athleteId IN :athleteIds
              AND i.status = br.com.menthoros.backend.enums.InvoiceStatus.OPEN
            """)
    List<AthleteOpenInvoiceView> findOpenByTenantIdAndAthleteIdIn(@Param("tenantId") UUID tenantId,
                                                                  @Param("athleteIds") Collection<UUID> athleteIds);

    /**
     * Último vencimento por contrato, para calcular o próximo dos atletas sem mensalidade em
     * aberto (design D4). Tenant-aware: YES.
     */
    @Query("""
            SELECT i.contractId AS contractId, MAX(i.dueDate) AS lastDueDate
            FROM AthleteInvoice i
            WHERE i.tenantId = :tenantId
              AND i.contractId IN :contractIds
            GROUP BY i.contractId
            """)
    List<ContractLastDueDateView> findLastDueDateByContract(@Param("tenantId") UUID tenantId,
                                                            @Param("contractIds") Collection<UUID> contractIds);
}
