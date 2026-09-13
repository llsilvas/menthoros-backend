package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.ai.ledger.GenerationOutcome;
import br.com.menthoros.backend.ai.ledger.LlmCallResult;
import br.com.menthoros.backend.entity.LlmCall;
import br.com.menthoros.backend.repository.LlmCallRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Escrita transacional do ledger (add-plan-generation-ledger, design D12). Separado do
 * {@link LlmCallLedger} de propósito: o {@code catch} best-effort precisa ficar <b>fora</b> da
 * transação — dentro dela, uma exceção do repositório marcaria rollback-only e o commit falharia
 * depois do {@code catch}, derrubando a geração que a change promete nunca derrubar.
 *
 * <p>{@code REQUIRES_NEW}: a rota {@code plano} chama fora de transação, mas os listeners
 * assíncronos chamam dentro de uma — o rollback deles não pode apagar a linha. {@code timeout = 5}:
 * uma escrita bloqueada estoura antes de reter o permit do {@code LlmConcurrencyLimiter}.
 */
@Component
@RequiredArgsConstructor
public class LlmCallLedgerWriter {

    static final int TIMEOUT_SEGUNDOS = 5;

    private final LlmCallRepository repository;

    /**
     * Idempotent: NÃO — insere uma linha por chamada.
     * Side Effects: Database insert.
     * Tenant-aware: NÃO — grava o tenant recebido, que pode ser nulo (observabilidade técnica).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = TIMEOUT_SEGUNDOS)
    public UUID inserir(LlmCall chamada) {
        return repository.save(chamada).getId();
    }

    /**
     * Idempotent: SIM — reaplicar o mesmo resultado é no-op observável.
     * Side Effects: Database update.
     * Tenant-aware: NÃO.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = TIMEOUT_SEGUNDOS)
    public boolean atualizarResultado(UUID callId, LlmCallResult result, @Nullable String violationsJson) {
        return repository.atualizarResultado(callId, result, violationsJson) == 1;
    }

    /**
     * Grava o desfecho na chamada de maior {@code attempt} da requisição.
     * Idempotent: SIM.
     * Side Effects: Database update.
     * Tenant-aware: NÃO.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = TIMEOUT_SEGUNDOS)
    public boolean atualizarDesfecho(UUID generationRequestId, GenerationOutcome outcome) {
        return repository.findTopByGenerationRequestIdOrderByAttemptDescCreatedAtDesc(generationRequestId)
                .map(ultima -> {
                    ultima.setRequestOutcome(outcome);
                    repository.save(ultima);
                    return true;
                })
                .orElse(false);
    }

    /**
     * Idempotent: SIM — anular de novo é no-op.
     * Side Effects: Database update.
     * Tenant-aware: NÃO.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = TIMEOUT_SEGUNDOS)
    public int anonimizarRespostasDoAtleta(UUID atletaId) {
        return repository.anonimizarRespostasDoAtleta(atletaId);
    }

    /**
     * Idempotent: SIM — a segunda execução do dia anula 0.
     * Side Effects: Database update.
     * Tenant-aware: NÃO — cross-tenant por natureza (D2).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = TIMEOUT_SEGUNDOS)
    public int purgarRespostasAntesDe(java.time.Instant corte) {
        return repository.purgarRespostasAntesDe(corte);
    }
}
