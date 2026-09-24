package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.entity.SugestaoCoach;
import br.com.menthoros.backend.enums.StatusSugestao;
import br.com.menthoros.backend.enums.TipoSugestao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
     * Lista as 3 sugestões mais recentes de um atleta dentro do tenant, priorizando qualquer
     * {@code PENDING} não-expirada antes de completar por {@code createdAt} DESC — garante que o
     * painel de sugestões recentes do coach ({@code RecentSuggestionsPanel}) sempre mostre uma
     * pendência sinalizada pelo badge (add-pending-suggestion-badge, design D6), mesmo que exista
     * uma pendência mais antiga que 3 sugestões já decididas. Limite aplicado no banco para evitar
     * carregamento desnecessário de registros.
     */
    @Query("""
       SELECT s FROM SugestaoCoach s JOIN FETCH s.atleta
       WHERE s.atleta.id = :atletaId
         AND s.tenantId = :tenantId
       ORDER BY CASE WHEN s.status = 'PENDING' AND (s.expiresAt IS NULL OR s.expiresAt > :agora) THEN 0 ELSE 1 END,
                s.createdAt DESC
       LIMIT 3
       """)
    List<SugestaoCoach> findAllByAtletaIdAndTenantId(@Param("atletaId") UUID atletaId,
                                                      @Param("tenantId") UUID tenantId,
                                                      @Param("agora") Instant agora);

    /**
     * IDs de atletas com {@link SugestaoCoach} no status dado, não expirada, no tenant —
     * resolvido uma vez para roster/calendário (add-pending-suggestion-badge), mesmo padrão de
     * {@code CoachAttentionQueueServiceImpl.getAttentionQueue()} (sem N+1). Mesma regra de
     * expiração de {@code SugestaoCoachServiceImpl.listar(PENDING)}.
     */
    @Query("""
       SELECT DISTINCT s.atleta.id FROM SugestaoCoach s
       WHERE s.tenantId = :tenantId AND s.status = :status
         AND (s.expiresAt IS NULL OR s.expiresAt > :agora)
       """)
    Set<UUID> findAtletaIdsByTenantIdAndStatus(@Param("tenantId") UUID tenantId,
                                                @Param("status") StatusSugestao status,
                                                @Param("agora") Instant agora);
}
