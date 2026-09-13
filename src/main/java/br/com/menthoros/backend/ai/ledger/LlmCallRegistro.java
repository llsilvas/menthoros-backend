package br.com.menthoros.backend.ai.ledger;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * O que o {@code CostTrackingAdvisor} sabe de uma chamada no momento da resposta (ou da exceção),
 * entregue ao {@code LlmCallLedger} para virar uma linha. Tokens e custo são nulos no caminho de
 * exceção; {@code responseText} só vem com contexto (rota plano) e é redigido antes de gravar.
 */
public record LlmCallRegistro(String route,
                              String model,
                              @Nullable Long inputTokens,
                              @Nullable Long outputTokens,
                              @Nullable Long cacheReadTokens,
                              @Nullable Long cacheWriteTokens,
                              @Nullable BigDecimal costUsd,
                              int latencyMs,
                              LlmCallResult result,
                              @Nullable UUID tenantId,
                              @Nullable Integer transportRetries,
                              @Nullable String responseText,
                              Optional<LlmCallContext> context) {

    public LlmCallRegistro {
        if (route == null || route.isBlank()) {
            throw new IllegalArgumentException("route é obrigatória");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model é obrigatório");
        }
        if (result == null) {
            throw new IllegalArgumentException("result é obrigatório");
        }
        if (context == null) {
            throw new IllegalArgumentException("context é obrigatório (use Optional.empty())");
        }
        if (latencyMs < 0) {
            throw new IllegalArgumentException("latencyMs não pode ser negativa");
        }
    }
}
