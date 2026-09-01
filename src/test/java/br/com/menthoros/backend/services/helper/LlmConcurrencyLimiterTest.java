package br.com.menthoros.backend.services.helper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o {@link LlmConcurrencyLimiter} e suas três faixas
 * (fair-llm-concurrency-per-tenant): global, cap por assessoria no lote e reserva interativa.
 *
 * <p>Teste puro (sem Spring). Sincronização por latch — nada de sleep como mecanismo de
 * ordenação; os poucos sleeps existentes só alargam a janela de observação de um máximo.
 */
@DisplayName("LlmConcurrencyLimiter")
class LlmConcurrencyLimiterTest {

    private static LlmConcurrencyLimiter limiter(int global, int capPorTenant, int reserva) {
        return new LlmConcurrencyLimiter(global, capPorTenant, reserva);
    }

    @Nested
    @DisplayName("executar (legado)")
    class Executar {

        @Test
        @DisplayName("retorna o valor produzido pela chamada")
        void retornaValor() throws InterruptedException {
            String resultado = limiter(4, 2, 1).executar(() -> "plano-gerado");

            assertThat(resultado).isEqualTo("plano-gerado");
        }

        @Test
        @DisplayName("nunca deixa mais chamadas em voo que o número de permits")
        void respeitaTetoDeConcorrencia() throws InterruptedException {
            int permits = 3;
            int tarefas = 12;
            LlmConcurrencyLimiter limiter = limiter(permits, 2, 0);

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

    @Nested
    @DisplayName("executarLote — cap por assessoria")
    class ExecutarLote {

        @Test
        @DisplayName("um tenant nunca passa do cap, mesmo com o global folgado")
        void tenantNaoPassaDoCap() throws Exception {
            LlmConcurrencyLimiter limiter = limiter(10, 2, 0);
            UUID tenant = UUID.randomUUID();

            Plato plato = medirPlato(6, () -> tenant, limiter, 2);

            assertThat(plato.maxEmVoo()).isEqualTo(2);
        }

        @Test
        @DisplayName("o cap de um tenant não limita outro — os dois progridem juntos")
        void capDeUmNaoLimitaOutro() throws Exception {
            LlmConcurrencyLimiter limiter = limiter(10, 2, 0);
            UUID tenantA = UUID.randomUUID();
            UUID tenantB = UUID.randomUUID();
            CountDownLatch aDentro = new CountDownLatch(2);
            CountDownLatch bDentro = new CountDownLatch(2);
            CountDownLatch libera = new CountDownLatch(1);

            try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
                for (int i = 0; i < 2; i++) {
                    pool.submit(() -> limiter.executarLote(tenantA, aguardando(aDentro, libera)));
                    pool.submit(() -> limiter.executarLote(tenantB, aguardando(bDentro, libera)));
                }
                // Com A saturado no cap 2, B ainda entra com os seus 2 — se o cap fosse global,
                // um dos latches nunca chegaria a zero.
                assertThat(aDentro.await(5, TimeUnit.SECONDS)).as("tenant A com 2 em voo").isTrue();
                assertThat(bDentro.await(5, TimeUnit.SECONDS)).as("tenant B com 2 em voo, apesar de A").isTrue();
                libera.countDown();
            }
        }

        @Test
        @DisplayName("a reserva interativa fica indisponível para o lote")
        void reservaIndisponivelParaOLote() throws Exception {
            // global 4, reserva 1 ⇒ lote usa no máximo 3, mesmo com cap por tenant folgado.
            LlmConcurrencyLimiter limiter = limiter(4, 10, 1);
            UUID tenant = UUID.randomUUID();

            Plato plato = medirPlato(6, () -> tenant, limiter, 3);

            assertThat(plato.maxEmVoo()).isEqualTo(3);
        }

        @Test
        @DisplayName("libera todas as faixas quando a chamada lança")
        void liberaEmErro() throws Exception {
            LlmConcurrencyLimiter limiter = limiter(1, 1, 0);
            UUID tenant = UUID.randomUUID();

            assertThatThrownBy(() -> limiter.executarLote(tenant, () -> {
                throw new IllegalStateException("provedor caiu");
            })).isInstanceOf(IllegalStateException.class);

            // Se qualquer faixa tivesse ficado presa, esta chamada travaria para sempre.
            assertThat(limiter.executarLote(tenant, () -> "ok")).isEqualTo("ok");
        }
    }

    @Nested
    @DisplayName("executarInterativo — reserva e reentrância")
    class ExecutarInterativo {

        @Test
        @DisplayName("entra na hora mesmo com o lote saturado — o permit reservado está livre por construção")
        void naoEsperaOLote() throws Exception {
            LlmConcurrencyLimiter limiter = limiter(4, 10, 1);
            UUID tenant = UUID.randomUUID();
            CountDownLatch loteDentro = new CountDownLatch(3);
            CountDownLatch libera = new CountDownLatch(1);

            try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
                for (int i = 0; i < 3; i++) {
                    pool.submit(() -> limiter.executarLote(tenant, aguardando(loteDentro, libera)));
                }
                assertThat(loteDentro.await(5, TimeUnit.SECONDS)).as("lote saturou os 3 permits dele").isTrue();

                Future<String> interativo = pool.submit(() -> limiter.executarInterativo(() -> "coach atendido"));
                assertThat(interativo.get(2, TimeUnit.SECONDS))
                        .as("o interativo não espera o lote drenar")
                        .isEqualTo("coach atendido");
                libera.countDown();
            }
        }

        @Test
        @DisplayName("usa a capacidade ociosa do lote quando não há lote rodando")
        void usaCapacidadeOciosa() throws Exception {
            // global 4, reserva 1: com o lote parado, o interativo pode chegar a 4 em voo.
            LlmConcurrencyLimiter limiter = limiter(4, 2, 1);
            CountDownLatch dentro = new CountDownLatch(4);
            CountDownLatch libera = new CountDownLatch(1);

            try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
                for (int i = 0; i < 4; i++) {
                    pool.submit(() -> limiter.executarInterativo(aguardando(dentro, libera)));
                }
                assertThat(dentro.await(5, TimeUnit.SECONDS))
                        .as("4 interativos em voo com global 4 — a reserva não limita o interativo")
                        .isTrue();
                libera.countDown();
            }
        }

        @Test
        @DisplayName("é no-op quando a thread já segura permits do lote (reentrância, sem deadlock)")
        void reentranteDentroDoLote() throws Exception {
            // global 1: se a reentrância falhar, o executarInterativo interno trava para sempre
            // esperando o permit que a própria thread segura.
            LlmConcurrencyLimiter limiter = limiter(1, 1, 0);
            UUID tenant = UUID.randomUUID();

            try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
                Future<String> resultado = pool.submit(() ->
                        limiter.executarLote(tenant, () -> {
                            try {
                                return limiter.executarInterativo(() -> "reentrou");
                            } catch (InterruptedException e) {
                                throw new IllegalStateException(e);
                            }
                        }));
                assertThat(resultado.get(2, TimeUnit.SECONDS)).isEqualTo("reentrou");
            }
        }
    }

    @Nested
    @DisplayName("configuração inválida falha no boot, não em runtime")
    class ConfiguracaoInvalida {

        @Test
        @DisplayName("reserva maior ou igual ao global")
        void reservaMaiorQueGlobal() {
            assertThatThrownBy(() -> limiter(4, 2, 4)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("cap por tenant menor que 1")
        void capMenorQueUm() {
            assertThatThrownBy(() -> limiter(4, 0, 1)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("global menor que 1")
        void globalMenorQueUm() {
            assertThatThrownBy(() -> limiter(0, 2, 0)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    /** Supplier que sinaliza a entrada e espera a liberação — sincronização por latch, sem sleep. */
    private static java.util.function.Supplier<Object> aguardando(CountDownLatch dentro, CountDownLatch libera) {
        return () -> {
            dentro.countDown();
            try {
                if (!libera.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("latch não liberado");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        };
    }

    private record Plato(int maxEmVoo) {
    }

    /**
     * Dispara {@code tarefas} pelo lote do mesmo tenant e mede o máximo em voo simultâneo,
     * esperando o platô esperado se formar antes de liberar (determinístico até o esperado;
     * o excesso é provado pelo máximo observado ao final).
     */
    private static Plato medirPlato(int tarefas, java.util.function.Supplier<UUID> tenant,
                                    LlmConcurrencyLimiter limiter, int platoEsperado) throws Exception {
        AtomicInteger emVoo = new AtomicInteger();
        AtomicInteger max = new AtomicInteger();
        CountDownLatch noPlato = new CountDownLatch(platoEsperado);
        CountDownLatch libera = new CountDownLatch(1);
        CountDownLatch fim = new CountDownLatch(tarefas);

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < tarefas; i++) {
                pool.submit(() -> {
                    try {
                        limiter.executarLote(tenant.get(), () -> {
                            int atual = emVoo.incrementAndGet();
                            max.accumulateAndGet(atual, Math::max);
                            noPlato.countDown();
                            try {
                                libera.await(10, TimeUnit.SECONDS);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
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
            assertThat(noPlato.await(5, TimeUnit.SECONDS)).as("platô de %d em voo", platoEsperado).isTrue();
            // Janela extra: se o limiter deixasse passar mais um, ele apareceria no máximo.
            sleepQuieto(100);
            libera.countDown();
            assertThat(fim.await(10, TimeUnit.SECONDS)).isTrue();
        }
        return new Plato(max.get());
    }

    private static void sleepQuieto(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
