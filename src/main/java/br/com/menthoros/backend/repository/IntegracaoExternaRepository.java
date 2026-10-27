package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.enums.FonteDados;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IntegracaoExternaRepository extends JpaRepository<IntegracaoExterna, UUID> {

    Optional<IntegracaoExterna> findByAtletaIdAndPlataforma(UUID atletaId, FonteDados plataforma);

    Optional<IntegracaoExterna> findByExternalAthleteIdAndPlataforma(String externalAthleteId, FonteDados plataforma);

    boolean existsByAtletaIdAndPlataforma(UUID atletaId, FonteDados plataforma);

    @Query("""
    select i from IntegracaoExterna i
    where i.atleta.id = :atletaId
      and i.plataforma = :plataforma
      and i.tenantId = :tenantId
    """)
    Optional<IntegracaoExterna> findByAtletaIdAndPlataformaAndTenantId(
            @Param("atletaId") UUID atletaId,
            @Param("plataforma") FonteDados plataforma,
            @Param("tenantId") UUID tenantId
    );

    @Query("""
    select i from IntegracaoExterna i
    where i.atleta.id = :atletaId
      and i.plataforma = :plataforma
      and i.tenantId = :tenantId
      and i.ativo = true
    """)
    Optional<IntegracaoExterna> findActiveByAtletaIdAndPlataformaAndTenantId(
            @Param("atletaId") UUID atletaId,
            @Param("plataforma") FonteDados plataforma,
            @Param("tenantId") UUID tenantId
    );

    /** D5 de garmin-connection: conexão de canal (intervals.icu ou Garmin) ativa; um por atleta pela exclusividade. */
    @Query("""
    select i from IntegracaoExterna i
    where i.atleta.id = :atletaId
      and i.plataforma in :plataformas
      and i.tenantId = :tenantId
      and i.ativo = true
    """)
    List<IntegracaoExterna> findActiveByAtletaIdAndPlataformaInAndTenantId(
            @Param("atletaId") UUID atletaId,
            @Param("plataformas") Collection<FonteDados> plataformas,
            @Param("tenantId") UUID tenantId
    );

    /**
     * D7 de garmin-connection: o canal que vale para o treinador — o ativo primeiro e, sem ativo, o mais
     * recente (distingue "revogada" de "nunca conectou"). Ativo antes de recente porque {@code ativar}
     * grava a linha do intervals.icu zerada depois da do Garmin, que a faria parecer a mais nova.
     */
    Optional<IntegracaoExterna> findFirstByAtletaIdAndPlataformaInAndTenantIdOrderByAtivoDescAtualizadoEmDesc(
            UUID atletaId, Collection<FonteDados> plataformas, UUID tenantId);

    /** D3 de garmin-connection: base do job diário — só olha {@code refresh_token_expira_em}, nunca {@code atualizado_em}. */
    @Query("""
    select i from IntegracaoExterna i
    where i.plataforma = :plataforma
      and i.ativo = true
      and i.refreshTokenExpiraEm < :limite
    """)
    List<IntegracaoExterna> findActiveByPlataformaAndRefreshTokenExpiraEmBefore(
            @Param("plataforma") FonteDados plataforma,
            @Param("limite") Instant limite
    );

    @Query("""
    select i from IntegracaoExterna i
    where i.externalAthleteId = :externalAthleteId
      and i.plataforma = :plataforma
      and i.ativo = true
    """)
    Optional<IntegracaoExterna> findActiveByExternalAthleteIdAndPlataforma(
            @Param("externalAthleteId") String externalAthleteId,
            @Param("plataforma") FonteDados plataforma
    );

    /**
     * D5.1: todas as conexões ativas de OUTROS atletas do mesmo tenant com o mesmo
     * {@code externalAthleteId} — usado para detectar cadastro incorreto (a mesma credencial
     * externa vinculada a dois atletas Menthoros distintos) antes de confiar em
     * {@code dto.athleteId() == conexao.externalAthleteId} como defesa suficiente.
     */
    @Query("""
    select i from IntegracaoExterna i
    where i.externalAthleteId = :externalAthleteId
      and i.plataforma = :plataforma
      and i.tenantId = :tenantId
      and i.ativo = true
      and i.atleta.id <> :atletaId
    """)
    List<IntegracaoExterna> findOtherActiveByExternalAthleteIdAndPlataformaAndTenantId(
            @Param("externalAthleteId") String externalAthleteId,
            @Param("plataforma") FonteDados plataforma,
            @Param("tenantId") UUID tenantId,
            @Param("atletaId") UUID atletaId
    );

    @Query("""
    select i from IntegracaoExterna i
    where i.plataforma = :plataforma
      and i.ativo = true
      and (i.autoSyncPausado = false or i.autoSyncPausado is null)
    """)
    List<IntegracaoExterna> findAllActiveByPlataforma(
            @Param("plataforma") FonteDados plataforma
    );

    @Query("SELECT COUNT(DISTINCT ie.atleta.id) FROM IntegracaoExterna ie WHERE ie.plataforma = 'STRAVA' AND ie.ativo = true")
    Integer countAthletesWithActiveStrava();

    /**
     * Único escritor de {@code pull_cursor} (mapeado somente leitura na entidade). {@code @Transactional}
     * porque {@code @Modifying} não abre transação, e os schedulers chamam sem uma. Participa da
     * transação do chamador quando há. Retorno {@code 0} = tenant errado ou integração removida.
     */
    @Transactional
    @Modifying(flushAutomatically = true)
    @Query("""
    update IntegracaoExterna i set i.pullCursor = :cursor
    where i.id = :id and i.tenantId = :tenantId
    """)
    int atualizarPullCursor(
            @Param("id") UUID id,
            @Param("tenantId") UUID tenantId,
            @Param("cursor") Instant cursor
    );

    /**
     * Status do sync sem salvar a entidade: uma instância carregada antes de uma chamada externa
     * está velha (ex.: {@code getValidToken} pode ter renovado e commitado os tokens no meio), e um
     * {@code save} dela desfaria o que outro escritor gravou.
     */
    @Transactional
    @Modifying(flushAutomatically = true)
    @Query("""
    update IntegracaoExterna i
       set i.ultimaSincronizacao = :ultima, i.syncActivityCount = :count, i.lastSyncError = :erro
     where i.id = :id and i.tenantId = :tenantId
    """)
    int atualizarStatusSync(
            @Param("id") UUID id,
            @Param("tenantId") UUID tenantId,
            @Param("ultima") Instant ultima,
            @Param("count") Integer count,
            @Param("erro") @Nullable String erro
    );
}
