package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.Prova;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProvaRepository extends JpaRepository<Prova, UUID> {

    /**
     * Busca provas de um atleta em um intervalo de datas, excluindo provas CANCELADAS.
     */
    @Query("""
        SELECT p FROM Prova p
        WHERE p.atleta = :atleta
          AND p.dataProva BETWEEN :dataInicio AND :dataFim
          AND p.statusProva != 'CANCELADA'
        ORDER BY p.dataProva ASC
        """)
    List<Prova> findByAtletaAndDataProvaBetweenOrderByDataProvaAsc(
            @Param("atleta") Atleta atleta,
            @Param("dataInicio") LocalDate dataInicio,
            @Param("dataFim") LocalDate dataFim
    );

    /**
     * Busca todas as provas de um atleta, excluindo provas CANCELADAS.
     */
    @Query("""
        SELECT p FROM Prova p
        WHERE p.atleta = :atleta
          AND p.statusProva != 'CANCELADA'
        ORDER BY p.dataProva ASC
        """)
    List<Prova> findByAtletaOrderByDataProvaAsc(@Param("atleta") Atleta atleta);

    /**
     * Busca a prova alvo do atleta, excluindo provas CANCELADAS.
     */
    @Query("""
        SELECT p FROM Prova p
        WHERE p.atleta = :atleta
          AND p.provaAlvo = true
          AND p.statusProva != 'CANCELADA'
        """)
    List<Prova> findByAtletaAndProvaAlvoTrue(@Param("atleta") Atleta atleta);

    /**
     * Busca as provas dos atletas do tenant nos próximos 15 dias,
     * ordenadas pela data ascendente (mais próximas primeiro).
     * Usa JOIN FETCH para eager-load o relacionamento com Atleta.
     *
     * Tenant-aware: YES — filtra por assessoria.id (o dashboard do coach só
     * enxerga a própria assessoria; a versão global vazava provas cross-tenant).
     */
    @Query("SELECT DISTINCT p FROM Prova p JOIN FETCH p.atleta WHERE p.assessoria.id = :tenantId AND p.dataProva >= CURRENT_DATE AND p.dataProva <= :endDate AND p.statusProva != 'CANCELADA' ORDER BY p.dataProva ASC")
    List<Prova> findUpcomingProvasNext15DaysByTenant(@Param("endDate") LocalDate endDate, @Param("tenantId") UUID tenantId);

    /**
     * Busca uma Prova filtrando por id e tenantId (assessoria.id).
     * Previne cross-tenant data leakage garantindo que a prova pertence ao tenant.
     *
     * Idempotent: YES — leitura pura.
     * Side Effects: NONE
     * Tenant-aware: YES
     */
    @Query("SELECT p FROM Prova p WHERE p.id = :id AND p.assessoria.id = :tenantId")
    Optional<Prova> findByIdAndTenantId(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    /**
     * IDs das provas-alvo não canceladas do atleta, da mais distante para a mais próxima. Só o ID, de
     * propósito: carregar a entidade aqui a deixaria no contexto de persistência, e o
     * {@link #findByIdAndTenantIdForUpdate} devolveria essa cópia sem reler o estado travado.
     * Tenant-aware: YES.
     */
    @Query("""
        SELECT p.id FROM Prova p
        WHERE p.atleta.id = :atletaId
          AND p.assessoria.id = :tenantId
          AND p.provaAlvo = true
          AND p.statusProva <> 'CANCELADA'
        ORDER BY p.dataProva DESC NULLS LAST
        """)
    List<UUID> findTargetRaceIds(@Param("atletaId") UUID atletaId, @Param("tenantId") UUID tenantId);

    /**
     * Lock pessimista da prova: serializa a geração da temporada com quem desmarca, cancela ou troca
     * a prova-alvo (add-season-plan-generate-tracer, D13). Só faz sentido dentro de uma transação.
     * Tenant-aware: YES.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Prova p WHERE p.id = :id AND p.assessoria.id = :tenantId")
    Optional<Prova> findByIdAndTenantIdForUpdate(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    @Query("""
        SELECT p FROM Prova p
        WHERE p.atleta.id = :id
          AND p.assessoria.id = :tenantId
          AND p.dataProva >= :dataMinima
          AND p.statusProva != 'CANCELADA'
        ORDER BY p.dataProva ASC
        """)
    List<Prova> findUpcomingByAtletaIdAndTenantId(
            @Param("id") UUID id,
            @Param("tenantId") UUID tenantId,
            @Param("dataMinima") LocalDate dataMinima
    );

    /**
     * Provas pendentes de ciência do coach no tenant: futuras (data >= hoje) ou canceladas, com
     * {@code revisadaPeloCoach = false}. Atleta em JOIN FETCH para o agrupamento na fila.
     *
     * Idempotent: YES · Side Effects: NONE · Tenant-aware: YES
     */
    @Query("""
        SELECT p FROM Prova p JOIN FETCH p.atleta
        WHERE p.assessoria.id = :tenantId
          AND p.revisadaPeloCoach = false
          AND (p.dataProva >= :hoje OR p.statusProva = 'CANCELADA')
        ORDER BY p.dataProva ASC
        """)
    List<Prova> findPendentesRevisaoByAssessoria(@Param("tenantId") UUID tenantId, @Param("hoje") LocalDate hoje);

    /**
     * Mesmo critério de {@link #findPendentesRevisaoByAssessoria}, para um atleta — alimenta o card
     * de provas no perfil do coach sem depender do corte da fila.
     *
     * Idempotent: YES · Side Effects: NONE · Tenant-aware: YES
     */
    @Query("""
        SELECT p FROM Prova p
        WHERE p.atleta.id = :atletaId
          AND p.assessoria.id = :tenantId
          AND p.revisadaPeloCoach = false
          AND (p.dataProva >= :hoje OR p.statusProva = 'CANCELADA')
        ORDER BY p.dataProva ASC
        """)
    List<Prova> findPendentesRevisaoByAtleta(@Param("atletaId") UUID atletaId, @Param("tenantId") UUID tenantId,
                                             @Param("hoje") LocalDate hoje);

    /**
     * Valida se uma Prova pertence a um tenant específico.
     * Usado pelo TenantValidationAspect para validação de isolamento.
     */
    @Query("""
       SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM Prova p
       WHERE p.id = :id AND p.atleta.assessoria.id = :tenantId
       """)
    boolean existsByIdAndAtleta_TenantId(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    /**
     * Busca provas realizadas de um atleta com tempo registrado, a partir de uma data mínima,
     * ordenadas pela mais recente primeiro. Sem filtro de distância em SQL — `Prova.distanciaKm`
     * só é preenchido para distância customizada, então um filtro `BETWEEN` só nesse campo
     * ignoraria toda prova cadastrada pelo enum `DistanciaProva` (caminho normal). A resolução de
     * distância e o filtro de faixa válida acontecem em código (ver
     * ThresholdInferenceService.resolverDistanciaMetros).
     *
     * Idempotent: YES — leitura pura.
     * Side Effects: NONE
     * Tenant-aware: YES
     */
    @Query("""
        SELECT p FROM Prova p
        WHERE p.atleta.id = :atletaId AND p.assessoria.id = :tenantId
          AND p.foiRealizada = true AND p.tempoRealizado IS NOT NULL
          AND p.dataProva >= :dataMinima AND p.statusProva != 'CANCELADA'
        ORDER BY p.dataProva DESC
        """)
    List<Prova> findProvasRealizadasRecentes(
            @Param("atletaId") UUID atletaId,
            @Param("tenantId") UUID tenantId,
            @Param("dataMinima") LocalDate dataMinima
    );

    /** Provas por id restritas à assessoria (marcos da temporada). */
    List<Prova> findByIdInAndAssessoriaId(Collection<UUID> ids, UUID assessoriaId);
}
