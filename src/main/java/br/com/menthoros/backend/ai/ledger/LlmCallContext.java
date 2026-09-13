package br.com.menthoros.backend.ai.ledger;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Contexto imutável que a rota {@code plano} descreve antes de cada Chamada LLM
 * (add-plan-generation-ledger, design D3). Quem chama o LLM sabe de que geração, atleta e
 * tentativa se trata; o {@code CostTrackingAdvisor} só sabe de rota, modelo e usage — este record
 * é o que junta as duas metades numa linha só.
 *
 * @param atletaNome usado apenas para redigir o nome na resposta antes de gravar (D7); nunca é
 *                   persistido
 */
public record LlmCallContext(UUID generationRequestId,
                             @Nullable UUID atletaId,
                             @Nullable String atletaNome,
                             int attempt,
                             String promptVersion,
                             String promptHash,
                             String schemaVersion) {

    public LlmCallContext {
        if (generationRequestId == null) {
            throw new IllegalArgumentException("generationRequestId é obrigatório");
        }
        if (attempt < 1) {
            throw new IllegalArgumentException("attempt começa em 1, recebido " + attempt);
        }
        if (promptVersion == null || promptVersion.isBlank()) {
            throw new IllegalArgumentException("promptVersion é obrigatória");
        }
        if (promptHash == null || promptHash.isBlank()) {
            throw new IllegalArgumentException("promptHash é obrigatório");
        }
        if (schemaVersion == null || schemaVersion.isBlank()) {
            throw new IllegalArgumentException("schemaVersion é obrigatória");
        }
    }
}
