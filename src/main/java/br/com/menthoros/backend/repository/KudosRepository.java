package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.entity.Kudos;
import br.com.menthoros.backend.enums.MotivoKudos;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
     * Últimos 10 kudos de um atleta, mais recentes primeiro.
     * Tenant-aware: YES.
     */
    @Query("""
        SELECT k FROM Kudos k
        WHERE k.atleta.id = :atletaId AND k.tenantId = :tenantId
        ORDER BY k.createdAt DESC
        LIMIT 10
        """)
    List<Kudos> findTop10ByAtletaIdAndTenantIdOrderByCreatedAtDesc(
            @Param("atletaId") UUID atletaId, @Param("tenantId") UUID tenantId);
}
