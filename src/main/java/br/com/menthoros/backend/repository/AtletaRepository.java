package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.repository.custom.AtletaRepositoryCustom;
import br.com.menthoros.backend.repository.projection.AtletaListProjection;
import br.com.menthoros.backend.repository.projection.AtletaProjection;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AtletaRepository extends PagingAndSortingRepository<Atleta, UUID>, JpaSpecificationExecutor<Atleta>, AtletaRepositoryCustom {
    Optional<Atleta> findById(UUID id);

    Atleta save(Atleta entity);

    @Transactional(readOnly = true)
    @Query("""
    select distinct atl from Atleta atl
    left join fetch atl.diasDisponiveis
    where atl.assessoria.id = :tenantId
      and atl.ativo = 'ATIVO'
    order by atl.nome ASC
    """)
    List<Atleta> findAllAtletasWithDias(@Param("tenantId") UUID tenantId);

    @Transactional(readOnly = true)
    @Query("""
    select distinct atl from Atleta atl
    left join fetch atl.provas
    where atl.assessoria.id = :tenantId
      and atl.ativo = 'ATIVO'
    order by atl.nome ASC
    """)
    List<Atleta> findAllAtletasWithProvas(@Param("tenantId") UUID tenantId);

    @Transactional(readOnly = true)
    @Query("""
    select atl from Atleta atl
    where atl.assessoria.id = :tenantId
      and atl.ativo = 'ATIVO'
    order by atl.nome ASC
    """)
    List<Atleta> findAllAtletasWithBasicInfo(@Param("tenantId") UUID tenantId);

    @Transactional(readOnly = true)
    @Query("""
    select atl from Atleta atl
    where atl.assessoria.id = :tenantId
      and atl.ativo = 'ATIVO'
    order by atl.nome ASC
    """)
    List<Atleta> findAllAtletas(@Param("tenantId") UUID tenantId);

    /**
     * Atletas <b>ativos</b> do tenant, ordenados por nome.
     *
     * <p>Antes chamava-se {@code findAllByTenantIdOrderByNome} e não filtrava status — o que
     * tornava o soft delete inócuo: o atleta inativado continuava no roster do dashboard, na fila
     * de atenção e na métrica de adesão. Um atleta que saiu da assessoria não deve gerar alerta
     * para o coach nem puxar a aderência para baixo.
     *
     * <p>Quem precisar de inativos deve pedir explicitamente, numa query própria — o nome dizia
     * "all" e a intenção de todos os três consumidores era "ativos".
     */
    @Transactional(readOnly = true)
    @Query("""
    select atl from Atleta atl
    where atl.assessoria.id = :tenantId
      and atl.ativo = 'ATIVO'
    order by atl.nome ASC
    """)
    List<Atleta> findAtivosByTenantIdOrderByNome(@Param("tenantId") UUID tenantId);

    /**
     * Busca atleta por ID garantindo que pertence ao tenant correto.
     * Retorna vazio se o ID existir mas for de outro tenant (evita vazamento de dados).
     */
    @Transactional(readOnly = true)
    @Query("""
    select atl from Atleta atl
    where atl.id = :id
      and atl.assessoria.id = :tenantId
    """)
    Optional<Atleta> findByIdAndTenantId(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    /**
     * Busca atleta por email dentro do tenant (usado no vínculo Usuario↔Atleta e no convite).
     */
    Optional<Atleta> findByEmailAndAssessoria_Id(String email, UUID tenantId);

    /**
     * Busca atleta já vinculado a um usuário dentro do tenant.
     */
    Optional<Atleta> findByUsuario_IdAndAssessoria_Id(UUID usuarioId, UUID tenantId);

    /**
     * Busca atleta por id SEM filtro de tenant.
     *
     * <p><b>USO RESTRITO:</b> só pode ser chamado em fluxos sem {@code TenantContext} disponível,
     * onde o {@code atletaId} provém de uma fonte confiável emitida pela própria aplicação — hoje,
     * exclusivamente o callback OAuth do Strava (o {@code state}/atletaId é gerado em
     * {@code getAuthorizationUrl}). Em qualquer fluxo com tenant no contexto, use
     * {@link #findByIdAndTenantId(UUID, UUID)} para garantir isolamento.
     *
     * <p><b>Débito conhecido:</b> hoje o {@code state} do OAuth é o atletaId em texto plano (sem
     * nonce/HMAC vinculado à sessão) — vetor CSRF/IDOR no callback. Endurecimento rastreado em
     * change dedicada (deferido junto da família Strava).
     */
    @Transactional(readOnly = true)
    @Query("""
    select atl from Atleta atl
    where atl.id = :id
    """)
    Optional<Atleta> findByIdBasic(@Param("id") UUID id);

    @Transactional(readOnly = true)
    @Query("""
    select distinct atl from Atleta atl
    join IntegracaoExterna ie on ie.atleta.id = atl.id
    where ie.plataforma = 'STRAVA'
      and ie.ativo = true
      and ie.accessToken is not null
      and atl.ativo = 'ATIVO'
      and (ie.autoSyncPausado = false or ie.autoSyncPausado is null)
    order by atl.nome ASC
    """)
    List<Atleta> findAllWithStravaConnected();

    @Transactional(readOnly = true)
    @Query("SELECT a FROM Atleta a ORDER BY a.nome ASC")
    List<AtletaListProjection> findProjectedAtletas();

    @Transactional(readOnly = true)
    @Query("SELECT a FROM Atleta a WHERE a.assessoria.id = :tenantId ORDER BY a.nome ASC")
    List<AtletaProjection> findProjectedByTenant(@Param("tenantId") UUID tenantId);

    @Transactional
    @Modifying
    @Query("UPDATE Atleta a SET a.ativo = br.com.menthoros.backend.enums.AtletaStatus.INATIVO WHERE a.id = :id")
    int deactivateAthlete(@Param("id") UUID id);

    @Transactional
    @Modifying
    @Query("UPDATE Atleta a SET a.ativo = br.com.menthoros.backend.enums.AtletaStatus.ATIVO WHERE a.id = :id")
    int activateAthlete(@Param("id") UUID id);

    @Transactional(readOnly = true)
    @Query("SELECT COUNT(a) FROM Atleta a")
    Integer countAllAthletes();

    /**
     * Conta atletas ativos de um tenant. Agregação no banco — carregar a coleção
     * {@code Assessoria.atletas} para chamar {@code size()} traria todas as linhas para contar.
     */
    @Transactional(readOnly = true)
    @Query("""
    SELECT COUNT(a) FROM Atleta a
    WHERE a.assessoria.id = :tenantId
      AND a.ativo = br.com.menthoros.backend.enums.AtletaStatus.ATIVO
    """)
    long countAtivosByTenantId(@Param("tenantId") UUID tenantId);
}
