package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.entity.Kudos;
import br.com.menthoros.backend.enums.MotivoKudos;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface KudosRepository extends JpaRepository<Kudos, UUID> {

    /**
     * Verifica se já existe um kudo do mesmo motivo, do mesmo coach, para o mesmo atleta, na
     * mesma data — usado para bloquear duplicata de duplo-clique/retry (D0.6).
     * Tenant-aware: YES (atletaId e coachId já são escopados ao tenant pelo caller).
     */
    boolean existsByAtletaIdAndCoachIdAndMotivoAndData(
            UUID atletaId, UUID coachId, MotivoKudos motivo, LocalDate data);

    /**
     * Kudos de um atleta dentro de uma janela de tempo (createdAt >= desde), mais recentes
     * primeiro. Sem LIMIT — a janela de tempo é o único filtro de "recente".
     * Tenant-aware: YES.
     */
    @Query("""
        SELECT k FROM Kudos k
        WHERE k.atleta.id = :atletaId AND k.tenantId = :tenantId AND k.createdAt >= :desde
        ORDER BY k.createdAt DESC
        """)
    List<Kudos> findRecentesByAtletaIdAndTenantId(
            @Param("atletaId") UUID atletaId, @Param("tenantId") UUID tenantId, @Param("desde") Instant desde);
}
