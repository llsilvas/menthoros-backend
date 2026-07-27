package br.com.menthoros.backend.config.external;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryProperties;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prova que o teto por rota da task 1.2 não pode ser pago várias vezes.
 *
 * <p>O {@code RetryTemplate} auto-configurado do Spring AI retenta
 * {@code TransientAiException} <b>e</b> {@code ResourceAccessException} — e o
 * segundo é justamente o timeout. Com o default de 10 tentativas, a rota
 * {@code plano} (120s) teria pior caso de ~20 minutos segurando uma conexão do
 * pool, que é exatamente o cenário que a change existe para fechar.
 *
 * <p>Este bean substitui o do auto-config (que é {@code @ConditionalOnMissingBean}).
 */
class LlmRetryConfigTest {

    private RetryTemplate retryTemplate;

    @BeforeEach
    void setUp() {
        SpringAiRetryProperties props = new SpringAiRetryProperties();
        props.setMaxAttempts(3);
        props.getBackoff().setInitialInterval(Duration.ofMillis(1));
        props.getBackoff().setMaxInterval(Duration.ofMillis(2));
        props.getBackoff().setMultiplier(2);

        retryTemplate = new LlmRetryConfig().retryTemplate(props);
    }

    @Nested
    @DisplayName("retryTemplate")
    class Politica {

        @Test
        @DisplayName("timeout de leitura não é retentado")
        void timeoutNaoRetenta() {
            AtomicInteger tentativas = new AtomicInteger();

            assertThatThrownBy(() -> retryTemplate.execute(ctx -> {
                tentativas.incrementAndGet();
                throw new ResourceAccessException("timeout",
                        new SocketTimeoutException("Read timed out"));
            })).isInstanceOf(ResourceAccessException.class);

            assertThat(tentativas).hasValue(1);
        }

        @Test
        @DisplayName("5xx/429 continua sendo retentado até maxAttempts")
        void transienteRetenta() {
            AtomicInteger tentativas = new AtomicInteger();

            assertThatThrownBy(() -> retryTemplate.execute(ctx -> {
                tentativas.incrementAndGet();
                throw new TransientAiException("503");
            })).isInstanceOf(TransientAiException.class);

            assertThat(tentativas).hasValue(3);
        }

        @Test
        @DisplayName("sucesso na primeira tentativa não retenta")
        void sucessoNaoRetenta() {
            AtomicInteger tentativas = new AtomicInteger();

            String resultado = retryTemplate.execute(ctx -> {
                tentativas.incrementAndGet();
                return "ok";
            });

            assertThat(resultado).isEqualTo("ok");
            assertThat(tentativas).hasValue(1);
        }

        @Test
        @DisplayName("maxAttempts e backoff continuam vindo de spring.ai.retry")
        void respeitaPropriedades() {
            SpringAiRetryProperties props = new SpringAiRetryProperties();
            props.setMaxAttempts(2);
            props.getBackoff().setInitialInterval(Duration.ofMillis(1));
            props.getBackoff().setMaxInterval(Duration.ofMillis(2));
            props.getBackoff().setMultiplier(2);

            RetryTemplate comDuas = new LlmRetryConfig().retryTemplate(props);
            AtomicInteger tentativas = new AtomicInteger();

            assertThatThrownBy(() -> comDuas.execute(ctx -> {
                tentativas.incrementAndGet();
                throw new TransientAiException("503");
            })).isInstanceOf(TransientAiException.class);

            assertThat(tentativas).hasValue(2);
        }
    }
}
