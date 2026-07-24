package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.entity.RevisaoSemanal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RevisaoSemanalRepository extends JpaRepository<RevisaoSemanal, UUID> {

    /**
     * Revisão 1:1 de um plano semanal, <b>escopada ao tenant</b> via join
     * {@code plano_semanal → assessoria} (CA7) — base do upsert idempotente por
     * {@code plano_semanal_id} e da leitura coach-only.
     */
    @Query("""
            SELECT r FROM RevisaoSemanal r
            WHERE r.planoSemanal.id = :planoSemanalId
              AND r.planoSemanal.assessoria.id = :tenantId
            """)
    Optional<RevisaoSemanal> findByPlanoSemanalIdAndTenant(@Param("planoSemanalId") UUID planoSemanalId,
                                                           @Param("tenantId") UUID tenantId);
}
