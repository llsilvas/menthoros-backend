package br.com.menthoros.backend.exception;

/**
 * Exceção lançada quando acesso a um recurso é negado.
 *
 * Casos de uso:
 * - Tentativa de acessar recurso de outro tenant
 * - Permissões insuficientes
 * - Violação de isolamento de dados
 *
 * Mapeamento HTTP: 403 Forbidden
 */
public class AccessDeniedException extends RuntimeException {
    public AccessDeniedException(String message) {
        super(message);
    }

    public AccessDeniedException(String message, Throwable cause) {
        super(message, cause);
    }
}
