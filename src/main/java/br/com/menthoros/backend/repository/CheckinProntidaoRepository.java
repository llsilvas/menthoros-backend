package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.entity.CheckinProntidao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CheckinProntidaoRepository extends JpaRepository<CheckinProntidao, UUID> {

    /**
     * Busca o checkin de um atleta numa data específica, restrito ao tenant.
     * Tenant-aware: YES.
     */
    @Query("""
        SELECT c FROM CheckinProntidao c
        WHERE c.atleta.id = :atletaId AND c.data = :data AND c.tenantId = :tenantId
        """)
    Optional<CheckinProntidao> findByAtletaIdAndData(
            @Param("atletaId") UUID atletaId,
            @Param("data") LocalDate data,
            @Param("tenantId") UUID tenantId);

    /**
     * Busca o checkin mais recente de um atleta, restrito ao tenant.
     * Tenant-aware: YES.
     */
    @Query("""
        SELECT c FROM CheckinProntidao c
        WHERE c.atleta.id = :atletaId AND c.tenantId = :tenantId
        ORDER BY c.data DESC
        LIMIT 1
        """)
    Optional<CheckinProntidao> findTopByAtletaIdOrderByDataDesc(
            @Param("atletaId") UUID atletaId,
            @Param("tenantId") UUID tenantId);

    /**
     * Busca o histórico de checkins de um atleta num intervalo de datas, restrito ao tenant.
     * Tenant-aware: YES.
     */
    @Query("""
        SELECT c FROM CheckinProntidao c
        WHERE c.atleta.id = :atletaId AND c.data BETWEEN :dataInicio AND :dataFim AND c.tenantId = :tenantId
        ORDER BY c.data DESC
        """)
    List<CheckinProntidao> findByAtletaIdAndDataBetween(
            @Param("atletaId") UUID atletaId,
            @Param("dataInicio") LocalDate dataInicio,
            @Param("dataFim") LocalDate dataFim,
            @Param("tenantId") UUID tenantId);
}
