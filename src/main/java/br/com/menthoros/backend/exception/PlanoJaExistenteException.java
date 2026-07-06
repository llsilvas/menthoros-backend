package br.com.menthoros.backend.exception;

/**
 * Já existe um plano ativo (review status diferente de REJEITADO) para o atleta
 * na semana alvo. Subtipo de {@link DomainRuleViolationException} para permitir
 * distinguir duplicidade de outras violações de regra (ex.: "sem dias
 * disponíveis") sem depender do texto da mensagem.
 */
public class PlanoJaExistenteException extends DomainRuleViolationException {
    public PlanoJaExistenteException(String message) {
        super(message);
    }
}
