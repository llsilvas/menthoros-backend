package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.entity.TreinoReconciliacao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface TreinoReconciliacaoRepository extends JpaRepository<TreinoReconciliacao, Long> {

    /**
     * Encontra todos os eventos de reconciliação para uma atividade realizada específica,
     * ordenados por data de ocorrência em ordem descendente (mais recente primeiro).
     */
    @Query(value = """
            SELECT tr FROM TreinoReconciliacao tr
            WHERE tr.tenantId = :tenantId
              AND tr.treinoRealizado.id = :treinoRealizadoId
            ORDER BY tr.occurredAt DESC
            """)
    List<TreinoReconciliacao> findByTreinoRealizadoId(
            @Param("tenantId") String tenantId,
            @Param("treinoRealizadoId") UUID treinoRealizadoId
    );

    /**
     * Encontra o evento de reconciliação mais recente para uma atividade realizada.
     * Útil para obter o estado atual de reconciliação.
     */
    TreinoReconciliacao findTop1ByTenantIdAndTreinoRealizadoIdOrderByOccurredAtDesc(
            String tenantId,
            UUID treinoRealizadoId
    );

    /**
     * Encontra todos os eventos de reconciliação para um ator específico (por exemplo, um coach)
     * dentro de um período de tempo, para auditoria.
     */
    @Query(value = """
            SELECT tr FROM TreinoReconciliacao tr
            WHERE tr.tenantId = :tenantId
              AND tr.actorId = :actorId
              AND tr.occurredAt >= :startTime
              AND tr.occurredAt < :endTime
            ORDER BY tr.occurredAt DESC
            """)
    Page<TreinoReconciliacao> findByActorIdAndDateRange(
            @Param("tenantId") String tenantId,
            @Param("actorId") String actorId,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime,
            Pageable pageable
    );

    /**
     * Encontra todos os eventos de reconciliação para uma atividade realizada dentro de um período,
     * para histórico de reconciliação.
     */
    @Query(value = """
            SELECT tr FROM TreinoReconciliacao tr
            WHERE tr.tenantId = :tenantId
              AND tr.treinoRealizado.id = :treinoRealizadoId
              AND tr.occurredAt >= :startTime
              AND tr.occurredAt < :endTime
            ORDER BY tr.occurredAt DESC
            """)
    List<TreinoReconciliacao> findByTreinoRealizadoIdAndDateRange(
            @Param("tenantId") String tenantId,
            @Param("treinoRealizadoId") UUID treinoRealizadoId,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime
    );

    /**
     * Conta o número de eventos de reconciliação por tipo de ação para um período específico.
     * Útil para métricas e análise de reconciliação.
     */
    @Query(value = """
            SELECT COUNT(tr)
            FROM TreinoReconciliacao tr
            WHERE tr.tenantId = :tenantId
              AND tr.actionType = :actionType
              AND tr.occurredAt >= :startTime
              AND tr.occurredAt < :endTime
            """)
    long countByActionTypeAndDateRange(
            @Param("tenantId") String tenantId,
            @Param("actionType") String actionType,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime
    );
}
