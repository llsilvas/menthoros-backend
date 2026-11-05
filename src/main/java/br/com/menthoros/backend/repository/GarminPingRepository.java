package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.entity.GarminPing;
import br.com.menthoros.backend.enums.GarminPingStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Fila de pings {@code activityFiles}. Cross-tenant de propósito: o scheduler processa todos os
 * tenants e instala o {@code TenantContext} de cada ping (D3).
 */
public interface GarminPingRepository extends JpaRepository<GarminPing, UUID> {

    /** Pendentes já vencidos e ainda dentro da janela de 24 h, mais antigo (mais perto de expirar) primeiro. */
    @Query("""
            select p from GarminPing p
            where p.status = br.com.menthoros.backend.enums.GarminPingStatus.PENDING
              and p.nextAttemptAt <= :now
              and p.createdAt > :windowStart
            order by p.createdAt asc
            """)
    List<GarminPing> findPendingOldestFirst(@Param("now") Instant now,
                                            @Param("windowStart") Instant windowStart,
                                            Pageable pageable);

    /** Pendentes que passaram da janela de download: o link do Garmin já não vale. */
    @Query("""
            select p from GarminPing p
            where p.status = br.com.menthoros.backend.enums.GarminPingStatus.PENDING
              and p.createdAt <= :windowStart
            """)
    List<GarminPing> findExpired(@Param("windowStart") Instant windowStart);

    /** {@code SYNCING} cujo processamento começou antes de {@code before}: o processo caiu após o claim. */
    @Query("""
            select p from GarminPing p
            where p.status = br.com.menthoros.backend.enums.GarminPingStatus.SYNCING
              and p.syncingSince < :before
            """)
    List<GarminPing> findStuckSyncing(@Param("before") Instant before);

    @Transactional
    @Modifying
    @Query("delete from GarminPing p where p.status in :statuses and p.createdAt < :before")
    int deleteByStatusInAndCreatedAtBefore(@Param("statuses") Collection<GarminPingStatus> statuses,
                                           @Param("before") Instant before);
}
