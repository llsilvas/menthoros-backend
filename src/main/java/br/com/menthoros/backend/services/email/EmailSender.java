package br.com.menthoros.backend.services.email;

import br.com.menthoros.backend.exception.EmailDeliveryException;

/**
 * Carteiro do backend. Uma implementação por ambiente: SMTP fora de {@code local}/{@code test},
 * arquivo dentro deles — nunca log, porque a mensagem pode carregar um segredo.
 */
public interface EmailSender {

    /**
     * Envia uma mensagem.
     *
     * <p><strong>Idempotent:</strong> NO — cada chamada envia de novo.
     * <p><strong>Side Effects:</strong> External API (SMTP) ou escrita em disco.
     * <p><strong>Tenant-aware:</strong> NO.
     *
     * @throws EmailDeliveryException quando o transporte recusa a mensagem
     */
    void send(EmailMessage message);
}
