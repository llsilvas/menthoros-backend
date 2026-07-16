package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.enums.FonteDados;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
