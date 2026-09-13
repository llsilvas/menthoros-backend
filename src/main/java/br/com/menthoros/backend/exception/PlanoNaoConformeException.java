package br.com.menthoros.backend.exception;

import br.com.menthoros.backend.ai.ledger.Violacao;

import java.util.List;

/**
 * Plano gerado que viola a estrutura prescrita (compliance estágio 1). Subtipo de
 * {@link LLMException} para continuar acionando o retry do {@code PlanoResilienceService}, e
 * carrega as violações com as keys reais para o ledger gravar {@code violations} sem parsear a
 * mensagem (add-plan-generation-ledger, D6).
 */
public class PlanoNaoConformeException extends LLMException {

    private final transient List<Violacao> violacoes;

    public PlanoNaoConformeException(String message, List<Violacao> violacoes) {
        super(message);
        this.violacoes = violacoes == null ? List.of() : List.copyOf(violacoes);
    }

    public List<Violacao> violacoes() {
        return violacoes;
    }
}
