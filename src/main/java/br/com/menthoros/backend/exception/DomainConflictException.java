package br.com.menthoros.backend.exception;

/**
 * Estado atual do domínio impede a operação (precondição não satisfeita, conflito de conexão,
 * recurso já vinculado a outra entidade) — distinto de {@link DomainRuleViolationException}
 * (422, dado inválido) e {@link DomainNotFoundException} (404, recurso ausente). Mapeado para
 * HTTP 409.
 */
public class DomainConflictException extends RuntimeException {
    public DomainConflictException(String message) {
        super(message);
    }
}
