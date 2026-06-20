package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.entity.SugestaoCoach;
import br.com.menthoros.backend.enums.StatusSugestao;
import br.com.menthoros.backend.enums.TipoSugestao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SugestaoCoachRepository extends JpaRepository<SugestaoCoach, UUID> {

    /** Busca sugestão por id e tenant com atleta em JOIN FETCH — base de {@code detalhe()}. */
    @Query("SELECT s FROM SugestaoCoach s JOIN FETCH s.atleta WHERE s.id = :id AND s.tenantId = :tenantId")
    Optional<SugestaoCoach> findByIdAndTenantId(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    /** Lista sugestões de um tenant por status com atleta em JOIN FETCH — evita N+1 no {@code listar()}. */
    @Query("SELECT s FROM SugestaoCoach s JOIN FETCH s.atleta WHERE s.tenantId = :tenantId AND s.status = :status")
    List<SugestaoCoach> findByTenantIdAndStatus(@Param("tenantId") UUID tenantId, @Param("status") StatusSugestao status);

    /** Verifica ownership para @RequireTenant (TenantValidationRepository). */
    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    /** Idempotência na camada Java: evita INSERT quando já existe pending para (atleta, tipo). */
    boolean existsByAtletaIdAndTipoAndStatus(UUID atletaId, TipoSugestao tipo, StatusSugestao status);

    /**
     * Lista todas as sugestões de um atleta dentro do tenant, ordenadas por createdAt DESC.
     * Usado no perfil do atleta para o coach para exibir as 3 mais recentes.
     */
    @Query("""
       SELECT s FROM SugestaoCoach s JOIN FETCH s.atleta
       WHERE s.atleta.id = :atletaId
         AND s.tenantId = :tenantId
       ORDER BY s.createdAt DESC
       """)
    List<SugestaoCoach> findAllByAtletaIdAndTenantId(@Param("atletaId") UUID atletaId,
                                                      @Param("tenantId") UUID tenantId);
}
