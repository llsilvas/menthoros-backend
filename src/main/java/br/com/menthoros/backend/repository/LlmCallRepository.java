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
}
