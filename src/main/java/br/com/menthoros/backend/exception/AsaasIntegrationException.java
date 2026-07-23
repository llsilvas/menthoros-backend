package br.com.menthoros.backend.exception;

/**
 * Erro da integração com a API do Asaas (customer/subscription).
 *
 * <p>O Asaas é um serviço externo; uma falha sua não é erro interno (500) e sim
 * {@code 502 Bad Gateway} (ver {@code GlobalExceptionHandler}). A mensagem NUNCA
 * contém a API key nem o token de cartão.
 */
public class AsaasIntegrationException extends RuntimeException {

    public AsaasIntegrationException(String message) {
        super(message);
    }

    public AsaasIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
