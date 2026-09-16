package br.com.menthoros.backend.ai.ledger;

/**
 * Desfecho de uma Requisição de geração, gravado na última Chamada LLM do grupo (design D6).
 *
 * <p>Só existe quando o LLM chegou a produzir um plano aceito ({@code llmAceito}): distingue os
 * casos em que a última chamada é {@code SUCCESS} e mesmo assim não há plano persistido —
 * corrida perdida no índice de plano ativo, rejeição terminal do estágio 2, falha na persistência.
 * Falhas antes de uma chamada aceita não têm desfecho (a linha diz o suficiente).
 */
public enum GenerationOutcome {
    PERSISTED,
    CONFLICT,
    REJECTED_POST_LLM,
    PERSIST_ERROR
}
