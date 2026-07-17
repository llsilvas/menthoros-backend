package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.Prova;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
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
          AND p.dataProva >= :dataMinima
        ORDER BY p.dataProva DESC
        """)
    List<Prova> findProvasRealizadasRecentes(
            @Param("atletaId") UUID atletaId,
            @Param("tenantId") UUID tenantId,
            @Param("dataMinima") LocalDate dataMinima
    );
}
