package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.enums.PlanoReviewStatus;
import br.com.menthoros.backend.enums.PlanoStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlanoSemanalRepository extends JpaRepository<PlanoSemanal, UUID> {
    Optional<PlanoSemanal> findPlanoSemanalByAtletaIdAndTreinosPlanejadosDataTreino(UUID id, LocalDate localDate);

    @Query("""
              select ps from PlanoSemanal ps
              where ps.atleta.id = :atletaId
                and :data between ps.semanaInicio and ps.semanaFim
            """)
    Optional<PlanoSemanal> findByAtletaIdAndSemana(@Param("atletaId") UUID atletaId,
                                                   @Param("data") LocalDate data);

    Optional<PlanoSemanal> findTopByAtletaIdOrderBySemanaInicioDesc(UUID atletaId);

    Optional<PlanoSemanal> findByAtletaIdAndSemanaInicioBetween(UUID atletaId, LocalDate with, LocalDate with1);

    boolean existsByAtletaIdAndSemanaInicioLessThanEqualAndSemanaFimGreaterThanEqualAndStatusNot(UUID atletaId, LocalDate hoje, LocalDate hoje1, PlanoStatus status);

    Optional<PlanoSemanal> findByAtletaIdAndSemanaInicio(UUID atletaId, LocalDate semanaInicio);

    Optional<PlanoSemanal> findTopByAtletaIdAndSemanaInicioBeforeAndStatusOrderBySemanaInicioDesc(
            UUID atletaId, LocalDate semanaInicio, PlanoStatus status);

    @Query("""
                select ps from PlanoSemanal ps
                    where ps.atleta.id = :atletaId
                        and ps.status != 'CONCLUIDO'
            """)
    Optional<PlanoSemanal> findByAtletaId(UUID atletaId);

    /**
     * Busca um PlanoSemanal filtrando por id e tenantId (assessoria.id).
     * Previne cross-tenant data leakage garantindo que o plano pertence ao tenant.
     *
     * Idempotent: YES — leitura pura.
     * Side Effects: NONE
     * Tenant-aware: YES
     */
    @Query("SELECT ps FROM PlanoSemanal ps WHERE ps.id = :id AND ps.assessoria.id = :tenantId")
    Optional<PlanoSemanal> findByIdAndTenantId(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    /**
     * Busca o plano mais recente APROVADO de um atleta.
     * Usado pelo endpoint GET /api/v1/planos/{atletaId} quando caller é ATLETA.
     *
     * Idempotent: YES — leitura pura.
     * Side Effects: NONE
     * Tenant-aware: YES — atleta.assessoria.id valida tenant implicitamente
     */
    Optional<PlanoSemanal> findTopByAtletaIdAndReviewStatusOrderBySemanaInicioDesc(
            UUID atletaId, PlanoReviewStatus reviewStatus);

    /**
     * Lista todos os planos de um tenant com um reviewStatus específico, ordenados por semanaInicio ASC.
     * Usado pelo endpoint GET /api/v1/coach/planos/pendentes.
     *
     * Idempotent: YES — leitura pura.
     * Side Effects: NONE
     * Tenant-aware: YES
     */
    @Query("""
            SELECT ps FROM PlanoSemanal ps
            WHERE ps.assessoria.id = :tenantId
              AND ps.reviewStatus = :reviewStatus
            ORDER BY ps.semanaInicio ASC
            """)
    List<PlanoSemanal> findByAssessoriaIdAndReviewStatusOrderBySemanaInicioAsc(
            @Param("tenantId") UUID tenantId,
            @Param("reviewStatus") PlanoReviewStatus reviewStatus);

    /**
     * Valida se um PlanoSemanal pertence a um tenant específico.
     * Usado pelo TenantValidationAspect para validação de isolamento.
     */
    @Query("""
       SELECT CASE WHEN COUNT(ps) > 0 THEN true ELSE false END FROM PlanoSemanal ps
       WHERE ps.id = :id AND ps.atleta.assessoria.id = :tenantId
       """)
    boolean existsByIdAndAtleta_TenantId(@Param("id") UUID id, @Param("tenantId") UUID tenantId);
}
