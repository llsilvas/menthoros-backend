package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.exception.EmailDeliveryException;
import br.com.menthoros.backend.services.email.EmailMessage;
import br.com.menthoros.backend.services.email.EmailSender;
import lombok.RequiredArgsConstructor;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

/**
 * Bean dedicado só para o {@code @Retryable} funcionar de verdade — na mesma classe que também
 * captura a exceção (como em {@link WaitlistNotificationListener}), o proxy do Spring nunca
 * intercepta a chamada interna e o retry vira código morto (mesmo problema documentado em
 * {@code WeeklyFocusModelClient}).
 */
@Component
@RequiredArgsConstructor
class WaitlistEmailSender {

    private final EmailSender emailSender;

    /**
     * Idempotent: NO — cada chamada envia de novo.
     * Side Effects: External API call (SMTP ou outbox local).
     * Tenant-aware: NO.
     */
    @Retryable(retryFor = EmailDeliveryException.class, maxAttempts = 3, backoff = @Backoff(delay = 2000, multiplier = 2))
    void enviar(EmailMessage mensagem) {
        emailSender.send(mensagem);
    }
}
