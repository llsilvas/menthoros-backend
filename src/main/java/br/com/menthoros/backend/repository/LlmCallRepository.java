package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.ai.ledger.LlmCallResult;
import br.com.menthoros.backend.entity.LlmCall;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Acesso ao ledger de chamadas LLM (add-plan-generation-ledger). Só escrita pelo
 * {@code LlmCallLedgerWriter}; leitura analítica é por SQL/Grafana, sem endpoint (design D11).
 */
public interface LlmCallRepository extends JpaRepository<LlmCall, UUID> {

    @Modifying
    @Query("update LlmCall c set c.result = :result, c.violations = :violations where c.id = :id")
    int atualizarResultado(@Param("id") UUID id,
                           @Param("result") LlmCallResult result,
                           @Param("violations") String violations);

    Optional<LlmCall> findTopByGenerationRequestIdOrderByAttemptDescCreatedAtDesc(UUID generationRequestId);

    /**
     * Anula {@code response_json} do atleta (add-plan-generation-ledger, D7). Chamado no soft-delete
     * do atleta — hoje não há hard delete em {@code Atleta} (a FK {@code ON DELETE SET NULL} cobre
     * uma eventual erradicação física futura, ver {@code AtletaServiceImpl.deleteAtleta}).
     */
    @Modifying
    @Query("update LlmCall c set c.responseJson = null where c.atletaId = :atletaId and c.responseJson is not null")
    int anonimizarRespostasDoAtleta(@Param("atletaId") UUID atletaId);

    /** Purga diária de retenção (D8): anula respostas mais antigas que o corte, preserva o resto. */
    @Modifying
    @Query("update LlmCall c set c.responseJson = null where c.createdAt < :corte and c.responseJson is not null")
    int purgarRespostasAntesDe(@Param("corte") java.time.Instant corte);
}
