package br.com.menthoros.backend.exception;

/**
 * O transporte de e-mail (SMTP ou arquivo) recusou a mensagem.
 *
 * <p>Mapeada para 502 Bad Gateway no {@code GlobalExceptionHandler}: o provedor de e-mail é um
 * serviço externo, e a falha dele não é erro interno. A mensagem nunca carrega o corpo do e-mail.</p>
 */
public class EmailDeliveryException extends RuntimeException {

    public EmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
