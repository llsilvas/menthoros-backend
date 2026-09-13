package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.entity.LlmCall;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Acesso ao ledger de chamadas LLM (add-plan-generation-ledger). Só escrita pelo
 * {@code LlmCallLedger}; leitura analítica é por SQL/Grafana, sem endpoint (design D11).
 */
public interface LlmCallRepository extends JpaRepository<LlmCall, UUID> {
}
