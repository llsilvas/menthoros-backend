package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.entity.PlanoTreino;
import br.com.menthoros.backend.enums.SeasonPlanStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlanoTreinoRepository extends JpaRepository<PlanoTreino, UUID> {

    /** Motivos gravados em {@code versionReason} ao arquivar; texto exibido ao treinador. */
    String REASON_RACE_CANCELLED = "prova cancelada";
    String REASON_TARGET_REPLACED = "prova-alvo substituída";
    String REASON_TARGET_UNMARKED = "prova deixou de ser alvo";

    /** Busca a temporada (ou PlanoTreino legado) dentro do tenant. */
    Optional<PlanoTreino> findByIdAndAssessoriaId(UUID id, UUID tenantId);

    /**
     * Só o id da prova da temporada do atleta, sem carregar a entidade: quem aprova trava a prova antes
     * de ler a temporada, e uma cópia já carregada não seria relida depois do lock (D13).
     */
    @Query("""
            select p.prova.id from PlanoTreino p
             where p.id = :id and p.atleta.id = :atletaId and p.assessoria.id = :tenantId
            """)
    Optional<UUID> findRaceIdBySeasonPlan(@Param("id") UUID id, @Param("atletaId") UUID atletaId,
                                          @Param("tenantId") UUID tenantId);

    /** Temporadas do atleta nos status pedidos, dentro do tenant; legado (status nulo) fica de fora. */
    List<PlanoTreino> findByAtletaIdAndAssessoriaIdAndSeasonStatusIn(UUID atletaId, UUID tenantId,
                                                                     Collection<SeasonPlanStatus> statuses);

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
        List<PlanoTreino> abertas = findByProvaIdAndAssessoriaIdAndSeasonStatusIn(
                raceId, tenantId, List.of(SeasonPlanStatus.ACTIVE, SeasonPlanStatus.PROPOSED));
        // Altera as entidades em vez de UPDATE em massa: o bulk deixaria desatualizado um PlanoTreino
        // já carregado na transação, e limpar o contexto destacaria as entidades de quem chamou.
        abertas.forEach(plano -> {
            plano.setSeasonStatus(SeasonPlanStatus.ARCHIVED);
            plano.setVersionReason(reason);
        });
        saveAllAndFlush(abertas);
        return abertas.size();
    }

    /**
     * Considera só {@code prova_id}: é ele que governa a temporada. Plano legado que aponta a prova
     * apenas por {@code prova_alvo_id} tem {@code season_status} nulo e não teria o que arquivar.
     */
    List<PlanoTreino> findByProvaIdAndAssessoriaIdAndSeasonStatusIn(
            UUID raceId, UUID tenantId, Collection<SeasonPlanStatus> statuses);
}
