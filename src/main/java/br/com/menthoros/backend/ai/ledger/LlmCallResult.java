package br.com.menthoros.backend.ai.ledger;

/**
 * Resultado de uma Chamada LLM (add-plan-generation-ledger, design D6).
 *
 * <p>Na rota {@code plano} a linha nasce {@link #PENDING} e a rota a fecha depois da validação;
 * nas demais rotas o advisor grava {@link #SUCCESS} na hora (não há validação de domínio).
 * {@link #LLM_ERROR} e {@link #TIMEOUT} são escritos no caminho de exceção do provider.
 * Uma linha {@code PENDING} que nunca fechou (crash entre a resposta e a validação) é aceita como
 * tal e contada separadamente — nenhum job a "conserta".
 */
public enum LlmCallResult {
    PENDING,
    SUCCESS,
    VALIDATION_REJECTED,
    PARSE_ERROR,
    LLM_ERROR,
    TIMEOUT
}
