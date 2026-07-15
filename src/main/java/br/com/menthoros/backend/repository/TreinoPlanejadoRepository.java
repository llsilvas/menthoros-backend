package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.enums.TreinoExecucaoStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TreinoPlanejadoRepository extends BaseRepository<TreinoPlanejado, UUID>{
    List<TreinoPlanejado> findByAtletaId(UUID atletaId);

    @Query("SELECT COUNT(tp) FROM TreinoPlanejado tp WHERE tp.planoSemanal.id = :planoId AND tp.tenantId = :tenantId")
    long countByPlanoSemanalIdAndTenantId(@Param("planoId") UUID planoId, @Param("tenantId") UUID tenantId);

    Optional<TreinoPlanejado> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<TreinoPlanejado> findByIdAndPlanoSemanalIdAndTenantId(UUID id, UUID planoSemanalId, UUID tenantId);

    @Query("""
       SELECT tp FROM TreinoPlanejado tp
       WHERE tp.planoSemanal.id = :planoId
         AND tp.tenantId = :tenantId
         AND tp.statusTreino = br.com.menthoros.backend.enums.TreinoExecucaoStatus.PENDENTE
         AND tp.dataTreino <= :hoje
       """)
    List<TreinoPlanejado> findPendentesAteHojeDoPlano(@Param("planoId") UUID planoId,
                                                      @Param("tenantId") UUID tenantId,
                                                      @Param("hoje") LocalDate hoje);

    @Query("""
       select tp from TreinoPlanejado tp
       where tp.atleta.id = :atletaId
         and tp.dataTreino = :data
         and (:tipoTreino is null or tp.tipoTreino = :tipoTreino)
       """)
    Optional<TreinoPlanejado> matchByAtletaAndDateAndType(@Param("atletaId") UUID atletaId,
                                                          @Param("data") LocalDate data,
                                                          @Param("tipoTreino") TipoTreino tipoTreino);

    @Query("""
       select tp from TreinoPlanejado tp
       where tp.atleta.id = :atletaId
         and tp.dataTreino between :dataInicio and :dataFim
       order by tp.dataTreino ASC
       """)
    List<TreinoPlanejado> findByAtletaIdAndDataBetween(@Param("atletaId") UUID atletaId,
                                                        @Param("dataInicio") LocalDate dataInicio,
                                                        @Param("dataFim") LocalDate dataFim);

    /** Treinos planejados de todos os atletas de um tenant num intervalo (calendário do coach). */
    @Query("""
       select tp from TreinoPlanejado tp
       join fetch tp.atleta a
       where a.assessoria.id = :tenantId
         and tp.dataTreino between :dataInicio and :dataFim
       order by tp.dataTreino ASC
       """)
    List<TreinoPlanejado> findByTenantAndDataBetween(@Param("tenantId") UUID tenantId,
                                                      @Param("dataInicio") LocalDate dataInicio,
                                                      @Param("dataFim") LocalDate dataFim);

    @Query("""
       SELECT COUNT(tp) FROM TreinoPlanejado tp
       WHERE tp.atleta.id = :atletaId
         AND tp.dataTreino >= :weekStart
         AND tp.dataTreino <= :weekEnd
       """)
    Integer countPlannedTrainings(@Param("atletaId") UUID atletaId,
                                  @Param("weekStart") LocalDate weekStart,
                                  @Param("weekEnd") LocalDate weekEnd);

    /**
     * Busca o primeiro TreinoPlanejado sem realizado vinculado para best-effort match no registro manual.
     * Filtra por atleta, data, tipo e status elegível (PENDENTE ou PERDIDO), ordenado pelo mais antigo.
     */
    @Query("""
       SELECT tp FROM TreinoPlanejado tp
       WHERE tp.atleta.id = :atletaId
         AND tp.atleta.assessoria.id = :tenantId
         AND tp.dataTreino = :data
         AND tp.tipoTreino = :tipo
         AND tp.treinoRealizado IS NULL
         AND tp.statusTreino IN :statuses
       ORDER BY tp.criadoEm ASC
       """)
    Optional<TreinoPlanejado> findFirstForManualMatch(@Param("atletaId") UUID atletaId,
                                                       @Param("tenantId") UUID tenantId,
                                                       @Param("data") LocalDate data,
                                                       @Param("tipo") TipoTreino tipo,
                                                       @Param("statuses") List<TreinoExecucaoStatus> statuses);

    /**
     * Busca treinos planejados de um atleta a partir de uma data, com treinoRealizado em JOIN FETCH
     * para evitar N+1 no cálculo de aderência semanal.
     *
     * Idempotent: YES — leitura pura.
     * Side Effects: NONE.
     * Tenant-aware: YES — filtra por tenantId direto no TreinoPlanejado (herdado de TreinoBase).
     */
    @Query("""
       SELECT tp FROM TreinoPlanejado tp
       LEFT JOIN FETCH tp.treinoRealizado
       WHERE tp.atleta.id = :atletaId
         AND tp.tenantId = :tenantId
         AND tp.dataTreino >= :dataInicio
       ORDER BY tp.dataTreino ASC
       """)
    List<TreinoPlanejado> findComRealizadoByAtletaAndPeriodo(@Param("atletaId") UUID atletaId,
                                                              @Param("tenantId") UUID tenantId,
                                                              @Param("dataInicio") LocalDate dataInicio);

    /**
     * Valida se um TreinoPlanejado pertence a um tenant específico.
     * Usado pelo TenantValidationAspect para validação de isolamento.
     *
     * Porquê usar query customizada?
     * - Evita N+1 queries
     * - Usa índices do banco
     * - Retorna apenas booleano (leve)
     */
    @Query("""
       SELECT CASE WHEN COUNT(tp) > 0 THEN true ELSE false END FROM TreinoPlanejado tp
       WHERE tp.id = :id AND tp.atleta.assessoria.id = :tenantId
       """)
    boolean existsByIdAndAtleta_TenantId(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    /**
     * Treinos planejados aguardando retry do push para o intervals.icu. NUNCA inclui
     * {@code SINCRONIZANDO} (treino sendo processado agora pelo listener ou por outro worker
     * deste scheduler) nem {@code PENDENTE}/{@code NAO_SINCRONIZADO} (treino recém-aprovado ainda
     * não tocado pelo listener) — a precedência do listener sobre o retry é uma invariante de
     * segurança do fluxo de push (spec 3.3 + 8.2): tocar esses estados aqui duplicaria o
     * processamento de um treino que outro worker já reclamou.
     */
    @Query("""
        SELECT t FROM TreinoPlanejado t
        JOIN FETCH t.atleta a
        JOIN FETCH a.assessoria
        WHERE t.statusSincronizacao IN (br.com.menthoros.backend.enums.StatusSincronizacao.AGUARDANDO_RETRY,
                                        br.com.menthoros.backend.enums.StatusSincronizacao.ERRO_TEMPORARIO,
                                        br.com.menthoros.backend.enums.StatusSincronizacao.ERRO_LIMITE_RATE)
        """)
    List<TreinoPlanejado> findAllAguardandoRetryIntervalsIcu();

    /**
     * Treinos de um plano semanal, tenant-scoped — usado pelo listener de push do intervals.icu
     * para orquestrar sem percorrer a coleção lazy do plano fora de sessão (o reload fresco de
     * cada treino acontece na TX própria do IntervalsIcuPushProcessor).
     */
    List<TreinoPlanejado> findAllByPlanoSemanalIdAndTenantId(UUID planoSemanalId, UUID tenantId);
}
