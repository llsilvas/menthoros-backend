package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.exception.EmailDeliveryException;
import br.com.menthoros.backend.services.email.EmailMessage;
import br.com.menthoros.backend.services.email.EmailSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.retry.annotation.EnableRetry;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * {@code @Retryable} só funciona por trás do proxy do Spring Retry — instância criada com
 * {@code new} não retenta nada. Mesmo padrão de {@link WeeklyFocusModelClientRetryTest}.
 */
class WaitlistEmailSenderRetryTest {

    private final EmailSender emailSender = mock(EmailSender.class);

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class)
            .withBean(EmailSender.class, () -> emailSender);

    @TestConfiguration
    @EnableRetry
    static class TestConfig {
        @Bean
        WaitlistEmailSender waitlistEmailSender(EmailSender emailSender) {
            return new WaitlistEmailSender(emailSender);
        }
    }

    @Test
    @DisplayName("retenta até 3 vezes em EmailDeliveryException e sucede na 3ª")
    void retentaESucede() {
        EmailMessage mensagem = new EmailMessage("lead@exemplo.com", "Assunto", "<p>html</p>", "texto");
        doThrow(new EmailDeliveryException("falha 1", null))
                .doThrow(new EmailDeliveryException("falha 2", null))
                .doNothing()
                .when(emailSender).send(mensagem);

        contextRunner.run(ctx -> {
            WaitlistEmailSender sender = ctx.getBean(WaitlistEmailSender.class);
            sender.enviar(mensagem);
            verify(emailSender, times(3)).send(mensagem);
        });
    }

    @Test
    @DisplayName("esgota as 3 tentativas e propaga a exceção")
    void esgotaTentativasEPropaga() {
        EmailMessage mensagem = new EmailMessage("lead@exemplo.com", "Assunto", "<p>html</p>", "texto");
        doThrow(new EmailDeliveryException("sempre falha", null)).when(emailSender).send(mensagem);

        contextRunner.run(ctx -> {
            WaitlistEmailSender sender = ctx.getBean(WaitlistEmailSender.class);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> sender.enviar(mensagem))
                    .isInstanceOf(EmailDeliveryException.class);
            verify(emailSender, times(3)).send(mensagem);
        });
    }
}
