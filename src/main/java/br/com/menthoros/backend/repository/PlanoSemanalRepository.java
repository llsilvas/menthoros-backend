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

    /**
     * Busca o plano mais recente (não-CONCLUIDO) de um atleta dentro do tenant.
     *
     * Idempotent: YES — leitura pura.
     * Side Effects: NONE
     * Tenant-aware: YES — filtra por assessoria.id
     */
    @Query("""
                select ps from PlanoSemanal ps
                    where ps.atleta.id = :atletaId
                      and ps.assessoria.id = :tenantId
                      and ps.status != 'CONCLUIDO'
            """)
    Optional<PlanoSemanal> findByAtletaIdAndTenantId(@Param("atletaId") UUID atletaId,
                                                      @Param("tenantId") UUID tenantId);

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
     * Busca o plano mais recente APROVADO de um atleta, restrito ao tenant.
     * Usado pelo endpoint GET /api/v1/planos/{atletaId} quando caller é ATLETA.
     *
     * Idempotent: YES — leitura pura.
     * Side Effects: NONE
     * Tenant-aware: YES — filtra por assessoria.id explicitamente
     */
    Optional<PlanoSemanal> findTopByAtletaIdAndAssessoriaIdAndReviewStatusOrderBySemanaInicioDesc(
            UUID atletaId, UUID assessoriaId, PlanoReviewStatus reviewStatus);

    /**
     * Lista planos de um tenant com reviewStatus específico cuja semana ainda não encerrou
     * (semanaFim >= dataReferencia), ordenados por semanaInicio ASC.
     *
     * O filtro de data garante que apenas planos relevantes para a semana corrente
     * ou semanas futuras sejam retornados, excluindo histórico de semanas passadas.
     *
     * Idempotent: YES — leitura pura.
     * Side Effects: NONE
     * Tenant-aware: YES
     */
    @Query("""
            SELECT ps FROM PlanoSemanal ps
            WHERE ps.assessoria.id = :tenantId
              AND ps.reviewStatus = :reviewStatus
              AND ps.semanaFim >= :dataReferencia
            ORDER BY ps.semanaInicio ASC
            """)
    List<PlanoSemanal> findByAssessoriaIdAndReviewStatusOrderBySemanaInicioAsc(
            @Param("tenantId") UUID tenantId,
            @Param("reviewStatus") PlanoReviewStatus reviewStatus,
            @Param("dataReferencia") LocalDate dataReferencia);

    /**
     * Busca o plano mais recente do atleta cuja semana ainda não encerrou (semanaFim >= hoje),
     * independente do reviewStatus. Usado no perfil do atleta para o coach.
     *
     * <p>O filtro usa CURRENT_DATE do banco (não LocalDate.now() em Java) para evitar divergência
     * de fuso horário entre servidor e banco na virada de semana.
     *
     * Idempotent: YES — leitura pura.
     * Side Effects: NONE.
     * Tenant-aware: YES — filtra por assessoria.id.
     */
    @Query("""
       SELECT ps FROM PlanoSemanal ps
       WHERE ps.atleta.id = :atletaId
         AND ps.assessoria.id = :assessoriaId
         AND ps.semanaFim >= CURRENT_DATE
       ORDER BY ps.semanaInicio DESC
       LIMIT 1
       """)
    Optional<PlanoSemanal> findMostRecentRelevantPlano(@Param("atletaId") UUID atletaId,
                                                       @Param("assessoriaId") UUID assessoriaId);

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
