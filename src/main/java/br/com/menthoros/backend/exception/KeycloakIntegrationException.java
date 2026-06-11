package br.com.menthoros.backend.exception;

/**
 * Falha na integração com o Keycloak Admin REST API
 * (token, criação de Organization ou envio de convite).
 *
 * <p>Mapeada para 502 Bad Gateway no {@code GlobalExceptionHandler}.
 */
public class KeycloakIntegrationException extends RuntimeException {

    public KeycloakIntegrationException(String message) {
        super(message);
    }

    public KeycloakIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
