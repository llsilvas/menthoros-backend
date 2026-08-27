package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.ReconciliationStatus;
import br.com.menthoros.backend.enums.StatusSincronizacao;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TreinoRealizadoRepository extends PagingAndSortingRepository<TreinoRealizado, UUID> {

    Optional<TreinoRealizado> findById(UUID id);

    Optional<TreinoRealizado> findByIdAndTenantId(UUID id, UUID tenantId);

    TreinoRealizado save(TreinoRealizado entity);

    List<TreinoRealizado> findByAtletaIdOrderByDataTreinoAsc(UUID atletaId);

    Optional<TreinoRealizado> findByFonteDadosAndExternalId(FonteDados fonte, String externalId);

    Optional<TreinoRealizado> findByExternalIdAndAtletaId(String externalId, UUID atletaId);

    /**
     * Chave real da constraint {@code uk_treino_realizado_tenant_fonte_external} (V29).
     *
     * <p>{@code @EntityGraph} carrega {@code etapasRealizadas} (LAZY) na mesma query — achado do
     * smoke do Bloco 7 (intervals-icu-activity-ingestion): o passo 0 (dedup) de
     * {@code IntervalsIcuActivityIngestionServiceImpl} chama este método fora de transação
     * (orquestrador deliberadamente não-transacional) e mapeia o resultado direto para DTO; sem o
     * fetch eager, {@code TreinoMapperImpl.toOutputDto} → {@code DecouplingCalculatorService}
     * acessa a coleção lazy e lança {@code LazyInitializationException} sempre que
     * {@code open-in-view=false} (perfil {@code dev}, produção).
     *
     * <p>{@code sensacoes} está na lista pelo mesmo motivo, achado no DoR de
     * {@code athlete-training-loop}: o padrão do Spring Data para {@code @EntityGraph} sem
     * {@code type} explícito é {@code FETCH} (não {@code LOAD}) — nesse modo, todo atributo
     * **fora** da lista volta a LAZY na hora da query, mesmo que o mapeamento da entidade diga
     * EAGER. Marcar {@code sensacoes} como EAGER na entidade não bastou; precisou entrar aqui.
     */
    @EntityGraph(attributePaths = {"etapasRealizadas", "sensacoes"})
    Optional<TreinoRealizado> findByTenantIdAndFonteDadosAndExternalId(UUID tenantId, FonteDados fonteDados, String externalId);

    /**
     * Contagem por delta do scheduler do intervals.icu: {@code syncActivityCount} mede importações
     * NOVAS (antes/depois do lote), não chamadas bem-sucedidas — o import é idempotente e devolve
     * sucesso também para o que já existia (design D2, pre-mortem moderado #4).
     */
    long countByTenantIdAndAtletaIdAndFonteDados(UUID tenantId, UUID atletaId, FonteDados fonteDados);

    /**
     * Treinos de uma fonte externa que ficaram SEM etapas — candidatos ao backfill (D9 da change
     * {@code intervals-icu-activity-laps}).
     *
     * <p>São os importados antes da ingestão de etapas existir. O guard de dedup do import impede
     * corrigi-los reimportando: por isso o backfill atualiza o registro em vez de inserir outro.
     *
     * <p>{@code externalId} não-nulo é obrigatório — sem ele não há o que buscar na fonte.
     */
    @Query("""
            select t from TreinoRealizado t
            where t.tenantId = :tenantId
              and t.atleta.id = :atletaId
              and t.fonteDados = :fonteDados
              and t.externalId is not null
              and not exists (select 1 from EtapaRealizada e where e.treinoRealizado = t)
            order by t.dataTreino desc
            """)
    List<TreinoRealizado> findSemEtapasByAtletaAndFonte(@Param("tenantId") UUID tenantId,
                                                        @Param("atletaId") UUID atletaId,
                                                        @Param("fonteDados") FonteDados fonteDados);

    @Query("select coalesce(sum(t.distanciaKm),0) from TreinoRealizado t where t.planoSemanal.id = :planoSemanalId")
    double sumDistanciaByPlanoSemanalId(@Param("planoSemanalId") UUID planoSemanalId);

    List<TreinoRealizado> findByAtletaIdOrderByDataTreinoDesc(UUID atletaId);

    /** Treino realizado mais recente do atleta (LIMIT 1 derivado por "Top"). */
    Optional<TreinoRealizado> findTopByAtletaIdOrderByDataTreinoDesc(UUID atletaId);

    /**
     * Busca treinos realizados de um atleta a partir de uma data limite, ordenados pela data decrescente.
     *
     * <p>O {@code treinoPlanejado} vem em fetch join porque os consumidores desta query
     * ({@code TreinoHistoricoProvider}, projeção de prova) resolvem o tipo por
     * {@code TreinoRealizado.getTipoTreinoEfetivo()} — sem o join seria um SELECT por treino.
     * O relacionamento é {@code @OneToOne}, então o LEFT JOIN não multiplica linhas.
     */
    @Query("SELECT tr FROM TreinoRealizado tr LEFT JOIN FETCH tr.treinoPlanejado "
            + "WHERE tr.atleta = :atleta AND tr.dataTreino >= :dataLimite ORDER BY tr.dataTreino DESC")
    List<TreinoRealizado> findByAtletaAndDataTreinoGreaterThanEqualOrderByDataTreinoDesc(
            @Param("atleta") Atleta atleta,
            @Param("dataLimite") LocalDate dataLimite
    );

    List<TreinoRealizado> findByAtletaIdAndDataTreino(UUID atletaId, LocalDate data);

    /**
     * Treino que conta na carga do dia (D8): {@code CANCELADO} nunca conta; {@code NULL} conta
     * — é o estado normal dos caminhos FIT e manual, que não escrevem
     * {@code statusSincronizacao}. Em SQL/JPQL {@code <> CANCELADO} sozinho excluiria o NULL, por
     * isso o {@code OR ... IS NULL} explícito (achado do DoR, Codex #1).
     */
    @Query("SELECT t FROM TreinoRealizado t WHERE t.atleta.id = :atletaId AND t.dataTreino = :data "
            + "AND (t.statusSincronizacao IS NULL OR t.statusSincronizacao <> br.com.menthoros.backend.enums.StatusSincronizacao.CANCELADO)")
    List<TreinoRealizado> findQueContamByAtletaIdAndDataTreino(
            @Param("atletaId") UUID atletaId, @Param("data") LocalDate data);

    @Query("SELECT MIN(t.dataTreino) FROM TreinoRealizado t WHERE t.atleta.id = :atletaId")
    LocalDate findDataPrimeiroTreino(@Param("atletaId") UUID atletaId);

    List<TreinoRealizado> findByAtletaIdAndDataTreinoBetween(UUID id, LocalDate semanaInicio, LocalDate semanaFim);

    /**
     * Fetch join de {@code treinoPlanejado} pelo mesmo motivo de
     * {@link #findByAtletaAndDataTreinoGreaterThanEqualOrderByDataTreinoDesc}: a progressão resolve
     * o tipo do treino pelo vínculo com o planejado.
     */
    @Query("SELECT tr FROM TreinoRealizado tr LEFT JOIN FETCH tr.treinoPlanejado WHERE tr.atleta.id = :atletaId AND tr.tenantId = :tenantId AND tr.dataTreino BETWEEN :dataInicio AND :dataFim ORDER BY tr.dataTreino DESC")
    List<TreinoRealizado> findByAtletaIdAndTenantIdAndDataTreinoBetween(
            @Param("atletaId") UUID atletaId,
            @Param("tenantId") UUID tenantId,
            @Param("dataInicio") LocalDate dataInicio,
            @Param("dataFim") LocalDate dataFim);

    Optional<PlanoMetaDados> findByPlanoSemanalId(UUID planoSemanalId);

    List<TreinoRealizado> findTreinoRealizadosByAtleta(Atleta atleta);

    List<TreinoRealizado> findTreinoRealizadosByAtletaId(UUID atletaId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE TreinoRealizado t SET t.planoSemanal = null WHERE t.planoSemanal.id = :planoSemanalId")
    void desvinculardePlanoSemanal(@Param("planoSemanalId") UUID planoSemanalId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE TreinoRealizado t SET t.treinoPlanejado = null WHERE t.treinoPlanejado.id IN " +
           "(SELECT tp.id FROM TreinoPlanejado tp WHERE tp.planoSemanal.id = :planoSemanalId)")
    void desvinculardeTreinosPlanejados(@Param("planoSemanalId") UUID planoSemanalId);

    @Query("""
       select t from TreinoRealizado t
       where t.atleta.id = :atletaId
         and t.dataTreino = :dataTreino
         and t.statusSincronizacao = :status
       order by t.dataTreino ASC
       """)
    List<TreinoRealizado> findByAtletaIdAndDataTreinoAndStatusSincronizacao(
            @Param("atletaId") UUID atletaId,
            @Param("dataTreino") LocalDate dataTreino,
            @Param("status") StatusSincronizacao status);

    @Query("""
        select tr from TreinoRealizado tr
        where tr.tenantId = :tenantId
          and tr.atleta.id = :atletaId
          and tr.reconciliationStatus in :statuses
          and (:dataInicio is null or tr.dataTreino >= :dataInicio)
          and (:dataFim is null or tr.dataTreino <= :dataFim)
        order by tr.dataTreino desc
        """)
    Page<TreinoRealizado> findPendentesParaRevisao(
            @Param("tenantId") UUID tenantId,
            @Param("atletaId") UUID atletaId,
            @Param("statuses") List<ReconciliationStatus> statuses,
            @Param("dataInicio") LocalDate dataInicio,
            @Param("dataFim") LocalDate dataFim,
            Pageable pageable);

    /**
     * Busca uma TreinoRealizado com eager fetch de etapasRealizadas.
     * Use quando precisar serializar a entidade (evita LazyInitializationException).
     */
    @Query("""
        select distinct tr from TreinoRealizado tr
        left join fetch tr.etapasRealizadas
        where tr.id = :id
        """)
    Optional<TreinoRealizado> findByIdWithEtapas(@Param("id") UUID id);

    @Query("""
       SELECT COUNT(tr) FROM TreinoRealizado tr
       WHERE tr.atleta.id = :atletaId
         AND tr.dataTreino >= :weekStart
         AND tr.dataTreino <= :weekEnd
       """)
    Integer countRealizedTrainings(@Param("atletaId") UUID atletaId,
                                   @Param("weekStart") LocalDate weekStart,
                                   @Param("weekEnd") LocalDate weekEnd);

    @Query("""
       SELECT tr FROM TreinoRealizado tr
       WHERE tr.atleta.id = :atletaId
         AND tr.dataTreino >= :weekStart
         AND tr.dataTreino <= :weekEnd
       ORDER BY tr.dataTreino ASC
       """)
    List<TreinoRealizado> findRealizedTrainingsByWeek(@Param("atletaId") UUID atletaId,
                                                       @Param("weekStart") LocalDate weekStart,
                                                       @Param("weekEnd") LocalDate weekEnd);

    /**
     * Valida se um TreinoRealizado pertence a um tenant específico.
     * Usado pelo TenantValidationAspect para validação de isolamento.
     */
    @Query("""
       SELECT CASE WHEN COUNT(tr) > 0 THEN true ELSE false END FROM TreinoRealizado tr
       WHERE tr.id = :id AND tr.atleta.assessoria.id = :tenantId
       """)
    boolean existsByIdAndAtleta_TenantId(@Param("id") UUID id, @Param("tenantId") UUID tenantId);
}
