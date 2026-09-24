package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.entity.AthleteContract;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * O filtro de tenant é manual neste projeto: toda query recebe {@code tenantId} explícito.
 * A única exceção, {@link #findTenantIdsWithActiveContract}, existe justamente para o scheduler
 * descobrir em quais tenants trabalhar.
 */
@Repository
public interface AthleteContractRepository extends JpaRepository<AthleteContract, UUID> {

    /** Tenant-aware: YES. No máximo um, garantido por {@code uq_athlete_contract_active}. */
    @Query("""
            SELECT c FROM AthleteContract c
            WHERE c.athleteId = :athleteId
              AND c.tenantId = :tenantId
              AND c.endedAt IS NULL
            """)
    Optional<AthleteContract> findActiveByAthleteIdAndTenantId(@Param("athleteId") UUID athleteId,
                                                              @Param("tenantId") UUID tenantId);

    /**
     * Lock pessimista para serializar geração de mensalidade e mutações do contrato (design D3).
     * Só faz sentido dentro de uma transação. Tenant-aware: YES.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM AthleteContract c WHERE c.id = :id AND c.tenantId = :tenantId")
    Optional<AthleteContract> findByIdAndTenantIdForUpdate(@Param("id") UUID id,
                                                          @Param("tenantId") UUID tenantId);

    /** Tenant-aware: NO por design — é a lista de tenants que o scheduler vai percorrer. */
    @Query("SELECT DISTINCT c.tenantId FROM AthleteContract c WHERE c.endedAt IS NULL")
    List<UUID> findTenantIdsWithActiveContract();

    /** Tenant-aware: YES. */
    @Query("SELECT c FROM AthleteContract c WHERE c.tenantId = :tenantId AND c.endedAt IS NULL")
    List<AthleteContract> findActiveByTenantId(@Param("tenantId") UUID tenantId);

    /** Tenant-aware: YES. Para o roster: contratos ativos de um lote de atletas numa query. */
    @Query("""
            SELECT c FROM AthleteContract c
            WHERE c.tenantId = :tenantId
              AND c.athleteId IN :athleteIds
              AND c.endedAt IS NULL
            """)
    List<AthleteContract> findActiveByTenantIdAndAthleteIdIn(@Param("tenantId") UUID tenantId,
                                                             @Param("athleteIds") Collection<UUID> athleteIds);
}
