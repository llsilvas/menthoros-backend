package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.entity.PlanoTreino;
import br.com.menthoros.backend.enums.SeasonPlanStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlanoTreinoRepository extends JpaRepository<PlanoTreino, UUID> {

    /** Busca a temporada (ou PlanoTreino legado) dentro do tenant. */
    Optional<PlanoTreino> findByIdAndAssessoriaId(UUID id, UUID tenantId);

    /**
     * Algum PlanoTreino do tenant aponta para a prova, como {@code prova_id} ou {@code prova_alvo_id},
     * em qualquer status (inclusive legado). Bloqueia a exclusão física da prova.
     */
    @Query("""
            select count(p) > 0 from PlanoTreino p
             where p.assessoria.id = :tenantId
               and (p.prova.id = :raceId or p.provaAlvo.id = :raceId)
            """)
    boolean existsReferencingRace(@Param("raceId") UUID raceId, @Param("tenantId") UUID tenantId);

    /**
     * Arquiva as temporadas abertas (ACTIVE e PROPOSED) da prova-alvo, sem apagar nada.
     * Idempotente: temporada já arquivada sai do filtro.
     *
     * @return quantas temporadas foram arquivadas
     */
    default int archiveOpenSeasons(UUID raceId, UUID tenantId, String reason) {
        return updateSeasonStatus(raceId, tenantId,
                List.of(SeasonPlanStatus.ACTIVE, SeasonPlanStatus.PROPOSED), SeasonPlanStatus.ARCHIVED, reason);
    }

    @Modifying
    @Query("""
            update PlanoTreino p
               set p.seasonStatus = :to, p.versionReason = :reason
             where p.assessoria.id = :tenantId
               and p.prova.id = :raceId
               and p.seasonStatus in :from
            """)
    int updateSeasonStatus(@Param("raceId") UUID raceId,
                           @Param("tenantId") UUID tenantId,
                           @Param("from") Collection<SeasonPlanStatus> from,
                           @Param("to") SeasonPlanStatus to,
                           @Param("reason") String reason);
}
