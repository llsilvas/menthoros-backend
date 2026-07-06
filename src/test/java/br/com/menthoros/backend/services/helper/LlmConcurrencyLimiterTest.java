package br.com.menthoros.backend.services.helper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD — Section 1.4: testa o {@link LlmConcurrencyLimiter}.
 *
 * <p>Teste puro (sem Spring): verifica o retorno da chamada e o teto de
 * concorrência real imposto pelo Semaphore.
 */
@DisplayName("LlmConcurrencyLimiter")
class LlmConcurrencyLimiterTest {

    @Nested
    @DisplayName("executar")
    class Executar {

        @Test
        @DisplayName("retorna o valor produzido pela chamada")
        void retornaValor() throws InterruptedException {
            LlmConcurrencyLimiter limiter = new LlmConcurrencyLimiter(4);

            String resultado = limiter.executar(() -> "plano-gerado");

            assertThat(resultado).isEqualTo("plano-gerado");
        }

        @Test
        @DisplayName("nunca deixa mais chamadas em voo que o número de permits")
        void respeitaTetoDeConcorrencia() throws InterruptedException {
            int permits = 3;
            int tarefas = 12;
            LlmConcurrencyLimiter limiter = new LlmConcurrencyLimiter(permits);

            AtomicInteger emVoo = new AtomicInteger(0);
            AtomicInteger maxObservado = new AtomicInteger(0);
            CountDownLatch fim = new CountDownLatch(tarefas);

            try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
                for (int i = 0; i < tarefas; i++) {
                    pool.submit(() -> {
                        try {
                            limiter.executar(() -> {
                                int atual = emVoo.incrementAndGet();
                                maxObservado.accumulateAndGet(atual, Math::max);
                                sleepQuieto(30);
                                emVoo.decrementAndGet();
                                return null;
                            });
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            fim.countDown();
                        }
                    });
                }
                assertThat(fim.await(10, TimeUnit.SECONDS)).isTrue();
            }

            assertThat(maxObservado.get()).isLessThanOrEqualTo(permits);
            assertThat(emVoo.get()).isZero();
        }
    }

    private static void sleepQuieto(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
