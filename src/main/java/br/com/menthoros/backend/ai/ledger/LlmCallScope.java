package br.com.menthoros.backend.ai.ledger;

import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * Escopo por thread da Chamada LLM (add-plan-generation-ledger, design D3): dois níveis de
 * contexto mais dois canais mutáveis, todos em {@code ThreadLocal} simples (nunca inheritable —
 * mesmo padrão do {@code TenantContext}: virtual threads do lote abrem o próprio escopo).
 *
 * <ul>
 *   <li><b>Requisição</b> ({@link #openRequest}): quem orquestra a geração (id da requisição,
 *       atleta). Aberto uma vez por geração de plano, na thread que chama o {@code IaService}.</li>
 *   <li><b>Tentativa</b> ({@link #openAttempt}): quem chama o modelo (número da tentativa, versões).
 *       Aberto por chamada; {@link #closeAttempt()} limpa também o id e o contador de retries.</li>
 *   <li>{@link #registerCallId}: escrito pelo advisor depois de gravar a linha.</li>
 *   <li>{@link #incrementTransportRetry()}: escrito pelo {@code RetryListener} de transporte,
 *       que roda na mesma thread da chamada (design D13).</li>
 * </ul>
 *
 * {@link #current()} só existe com requisição <b>e</b> tentativa abertas: sem tentativa não há o
 * que enriquecer, e o advisor grava a linha genérica.
 */
public final class LlmCallScope {

    private record Request(UUID generationRequestId, @Nullable UUID atletaId, @Nullable String atletaNome) {}

    private record Attempt(int attempt, String promptVersion, String promptHash, String schemaVersion) {}

    private static final ThreadLocal<Request> REQUEST = new ThreadLocal<>();
    private static final ThreadLocal<Attempt> ATTEMPT = new ThreadLocal<>();
    private static final ThreadLocal<UUID> LAST_CALL_ID = new ThreadLocal<>();
    private static final ThreadLocal<int[]> TRANSPORT_RETRIES = new ThreadLocal<>();

    private LlmCallScope() {
    }

    public static void openRequest(UUID generationRequestId, @Nullable UUID atletaId, @Nullable String atletaNome) {
        if (generationRequestId == null) {
            throw new IllegalArgumentException("generationRequestId é obrigatório");
        }
        REQUEST.set(new Request(generationRequestId, atletaId, atletaNome));
    }

    /** Fecha a requisição e tudo que estiver pendurado nela (tentativa, id, retries). */
    public static void closeRequest() {
        REQUEST.remove();
        closeAttempt();
    }

    public static void openAttempt(int attempt, String promptVersion, String promptHash, String schemaVersion) {
        if (attempt < 1) {
            throw new IllegalArgumentException("attempt começa em 1, recebido " + attempt);
        }
        ATTEMPT.set(new Attempt(attempt, promptVersion, promptHash, schemaVersion));
        LAST_CALL_ID.remove();
        TRANSPORT_RETRIES.remove();
    }

    /** Fecha a tentativa, o id da chamada e o contador de retries; a requisição continua aberta. */
    public static void closeAttempt() {
        ATTEMPT.remove();
        LAST_CALL_ID.remove();
        TRANSPORT_RETRIES.remove();
    }

    public static Optional<LlmCallContext> current() {
        Request request = REQUEST.get();
        Attempt attempt = ATTEMPT.get();
        if (request == null || attempt == null) {
            return Optional.empty();
        }
        return Optional.of(new LlmCallContext(request.generationRequestId(), request.atletaId(),
                request.atletaNome(), attempt.attempt(), attempt.promptVersion(), attempt.promptHash(),
                attempt.schemaVersion()));
    }

    public static Optional<UUID> currentGenerationRequestId() {
        return Optional.ofNullable(REQUEST.get()).map(Request::generationRequestId);
    }

    public static void registerCallId(UUID callId) {
        if (callId == null) {
            LAST_CALL_ID.remove();
        } else {
            LAST_CALL_ID.set(callId);
        }
    }

    public static Optional<UUID> lastCallId() {
        return Optional.ofNullable(LAST_CALL_ID.get());
    }

    public static void incrementTransportRetry() {
        int[] contador = TRANSPORT_RETRIES.get();
        if (contador == null) {
            contador = new int[1];
            TRANSPORT_RETRIES.set(contador);
        }
        contador[0]++;
    }

    /** Retries de transporte da tentativa corrente; {@code empty} fora de uma tentativa. */
    public static Optional<Integer> transportRetries() {
        if (ATTEMPT.get() == null) {
            return Optional.empty();
        }
        int[] contador = TRANSPORT_RETRIES.get();
        return Optional.of(contador == null ? 0 : contador[0]);
    }
}
