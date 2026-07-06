package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.entity.BatchPlanJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BatchPlanJobRepository extends JpaRepository<BatchPlanJob, UUID> {

    /**
     * Busca o job garantindo o isolamento de tenant (usado no GET de status).
     */
    Optional<BatchPlanJob> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * Incremento atômico do contador de gerados — UPDATE direto no banco, sem
     * ler/reatribuir em memória. Evita race condition e perda de incremento
     * entre virtual threads concorrentes escrevendo no mesmo job.
     */
    @Modifying
    @Query("UPDATE BatchPlanJob b SET b.gerados = b.gerados + 1 WHERE b.id = :id")
    void incrementarGerados(@Param("id") UUID id);

    /**
     * Incremento atômico do contador de erros — mesma garantia de
     * {@link #incrementarGerados(UUID)}.
     */
    @Modifying
    @Query("UPDATE BatchPlanJob b SET b.erros = b.erros + 1 WHERE b.id = :id")
    void incrementarErros(@Param("id") UUID id);
}
