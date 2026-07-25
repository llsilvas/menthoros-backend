package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.entity.RevisaoSemanal;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
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

    /**
     * Revisões do atleta (tenant-scoped) da mais recente para a mais antiga, por
     * {@code semanaInicio}. A leitura usa {@code Pageable} de tamanho 2 — [0] é a última revisão,
     * [1] (se houver) é a anterior, base do {@code weekOverWeekDelta} (CA9).
     */
    @Query("""
            SELECT r FROM RevisaoSemanal r
            WHERE r.planoSemanal.atleta.id = :atletaId
              AND r.planoSemanal.assessoria.id = :tenantId
            ORDER BY r.planoSemanal.semanaInicio DESC
            """)
    List<RevisaoSemanal> findRecentesByAtletaAndTenant(@Param("atletaId") UUID atletaId,
                                                       @Param("tenantId") UUID tenantId,
                                                       Pageable pageable);
}

