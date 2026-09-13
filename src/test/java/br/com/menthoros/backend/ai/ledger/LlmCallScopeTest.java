package br.com.menthoros.backend.ai.ledger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmCallScopeTest {

    private static final UUID REQ = UUID.randomUUID();
    private static final UUID ATLETA = UUID.randomUUID();

    @AfterEach
    void limpar() {
        LlmCallScope.closeRequest();
    }

    private static void abrirTudo() {
        LlmCallScope.openRequest(REQ, ATLETA, "Maria Souza");
        LlmCallScope.openAttempt(1, "plano-v1", "hash", "schema-v1");
    }

    @Nested
    @DisplayName("current")
    class Current {

        @Test
        @DisplayName("vazio sem nada aberto")
        void vazioSemNada() {
            assertThat(LlmCallScope.current()).isEmpty();
            assertThat(LlmCallScope.currentGenerationRequestId()).isEmpty();
        }

        @Test
        @DisplayName("vazio só com a requisição — sem tentativa não há o que enriquecer")
        void vazioSoComRequisicao() {
            LlmCallScope.openRequest(REQ, ATLETA, "Maria");
            assertThat(LlmCallScope.current()).isEmpty();
            assertThat(LlmCallScope.currentGenerationRequestId()).contains(REQ);
        }

        @Test
        @DisplayName("vazio só com a tentativa — tentativa sem requisição é bug do chamador, não enriquece")
        void vazioSoComTentativa() {
            LlmCallScope.openAttempt(1, "plano-v1", "hash", "schema-v1");
            assertThat(LlmCallScope.current()).isEmpty();
        }

        @Test
        @DisplayName("junta requisição e tentativa num contexto completo")
        void completo() {
            abrirTudo();
            LlmCallContext ctx = LlmCallScope.current().orElseThrow();
            assertThat(ctx.generationRequestId()).isEqualTo(REQ);
            assertThat(ctx.atletaId()).isEqualTo(ATLETA);
            assertThat(ctx.atletaNome()).isEqualTo("Maria Souza");
            assertThat(ctx.attempt()).isEqualTo(1);
            assertThat(ctx.promptVersion()).isEqualTo("plano-v1");
            assertThat(ctx.promptHash()).isEqualTo("hash");
            assertThat(ctx.schemaVersion()).isEqualTo("schema-v1");
        }

        @Test
        @DisplayName("requisição sem atleta (rota sem atleta) é permitida")
        void requisicaoSemAtleta() {
            LlmCallScope.openRequest(REQ, null, null);
            LlmCallScope.openAttempt(2, "plano-v1", "hash", "schema-v1");
            LlmCallContext ctx = LlmCallScope.current().orElseThrow();
            assertThat(ctx.atletaId()).isNull();
            assertThat(ctx.atletaNome()).isNull();
            assertThat(ctx.attempt()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("validação de entrada")
    class Validacao {

        @Test
        @DisplayName("openRequest sem id é erro")
        void requestSemId() {
            assertThatThrownBy(() -> LlmCallScope.openRequest(null, ATLETA, "x"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
        @DisplayName("openAttempt começa em 1")
        void attemptMenorQueUm(int attempt) {
            assertThatThrownBy(() -> LlmCallScope.openAttempt(attempt, "v", "h", "s"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("LlmCallContext rejeita versões em branco e id nulo")
        void contextoRejeitaCamposObrigatorios() {
            assertThatThrownBy(() -> new LlmCallContext(null, ATLETA, "n", 1, "v", "h", "s"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new LlmCallContext(REQ, ATLETA, "n", 1, " ", "h", "s"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new LlmCallContext(REQ, ATLETA, "n", 1, "v", "", "s"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new LlmCallContext(REQ, ATLETA, "n", 1, "v", "h", null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new LlmCallContext(REQ, ATLETA, "n", 0, "v", "h", "s"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("lastCallId e transportRetries")
    class Canais {

        @Test
        @DisplayName("registerCallId fica disponível na tentativa e some no closeAttempt")
        void callIdPorTentativa() {
            abrirTudo();
            UUID id = UUID.randomUUID();
            LlmCallScope.registerCallId(id);
            assertThat(LlmCallScope.lastCallId()).contains(id);

            LlmCallScope.closeAttempt();
            assertThat(LlmCallScope.lastCallId()).isEmpty();
            assertThat(LlmCallScope.currentGenerationRequestId()).as("requisição sobrevive ao closeAttempt").contains(REQ);
        }

        @Test
        @DisplayName("registerCallId(null) limpa o id")
        void callIdNuloLimpa() {
            abrirTudo();
            LlmCallScope.registerCallId(UUID.randomUUID());
            LlmCallScope.registerCallId(null);
            assertThat(LlmCallScope.lastCallId()).isEmpty();
        }

        @Test
        @DisplayName("abrir a próxima tentativa zera o id e o contador da anterior")
        void proximaTentativaZera() {
            abrirTudo();
            LlmCallScope.registerCallId(UUID.randomUUID());
            LlmCallScope.incrementTransportRetry();

            LlmCallScope.openAttempt(2, "plano-v1", "hash", "schema-v1");

            assertThat(LlmCallScope.lastCallId()).isEmpty();
            assertThat(LlmCallScope.transportRetries()).contains(0);
            assertThat(LlmCallScope.current().orElseThrow().attempt()).isEqualTo(2);
        }

        @Test
        @DisplayName("transportRetries: vazio fora de tentativa, 0 sem retry, N após N incrementos")
        void contadorDeRetries() {
            assertThat(LlmCallScope.transportRetries()).isEmpty();
            abrirTudo();
            assertThat(LlmCallScope.transportRetries()).contains(0);
            LlmCallScope.incrementTransportRetry();
            LlmCallScope.incrementTransportRetry();
            assertThat(LlmCallScope.transportRetries()).contains(2);
        }

        @Test
        @DisplayName("incremento fora de tentativa não vaza para a próxima tentativa")
        void incrementoForaDeTentativaNaoVaza() {
            LlmCallScope.incrementTransportRetry();
            abrirTudo();
            assertThat(LlmCallScope.transportRetries()).contains(0);
        }

        @Test
        @DisplayName("closeRequest limpa os quatro canais")
        void closeRequestLimpaTudo() {
            abrirTudo();
            LlmCallScope.registerCallId(UUID.randomUUID());
            LlmCallScope.incrementTransportRetry();

            LlmCallScope.closeRequest();

            assertThat(LlmCallScope.current()).isEmpty();
            assertThat(LlmCallScope.currentGenerationRequestId()).isEmpty();
            assertThat(LlmCallScope.lastCallId()).isEmpty();
            assertThat(LlmCallScope.transportRetries()).isEmpty();
        }

        @Test
        @DisplayName("fechar sem ter aberto é no-op")
        void fecharSemAbrir() {
            LlmCallScope.closeAttempt();
            LlmCallScope.closeRequest();
            assertThat(LlmCallScope.current()).isEmpty();
        }
    }

    @Nested
    @DisplayName("isolamento entre threads")
    class Isolamento {

        @Test
        @DisplayName("o escopo de uma thread não aparece em outra — virtual threads do lote abrem o próprio")
        void naoVazaEntreThreads() throws InterruptedException {
            abrirTudo();
            AtomicReference<Optional<LlmCallContext>> visto = new AtomicReference<>();
            AtomicReference<Optional<Integer>> retriesVistos = new AtomicReference<>();
            CountDownLatch pronto = new CountDownLatch(1);

            Thread.ofVirtual().start(() -> {
                visto.set(LlmCallScope.current());
                retriesVistos.set(LlmCallScope.transportRetries());
                pronto.countDown();
            });
            pronto.await();

            assertThat(visto.get()).isEmpty();
            assertThat(retriesVistos.get()).isEmpty();
            assertThat(LlmCallScope.current()).as("a thread original mantém o seu").isPresent();
        }

        @Test
        @DisplayName("duas threads com requisições diferentes não se misturam")
        void duasThreadsDoisContextos() throws InterruptedException {
            UUID req1 = UUID.randomUUID();
            UUID req2 = UUID.randomUUID();
            AtomicReference<UUID> visto1 = new AtomicReference<>();
            AtomicReference<UUID> visto2 = new AtomicReference<>();
            CountDownLatch ambosAbertos = new CountDownLatch(2);
            CountDownLatch liberar = new CountDownLatch(1);
            CountDownLatch terminou = new CountDownLatch(2);

            Runnable corpo1 = () -> corpo(req1, visto1, ambosAbertos, liberar, terminou);
            Runnable corpo2 = () -> corpo(req2, visto2, ambosAbertos, liberar, terminou);
            Thread.ofVirtual().start(corpo1);
            Thread.ofVirtual().start(corpo2);

            ambosAbertos.await();
            liberar.countDown();
            terminou.await();

            assertThat(visto1.get()).isEqualTo(req1);
            assertThat(visto2.get()).isEqualTo(req2);
        }

        private static void corpo(UUID req, AtomicReference<UUID> visto, CountDownLatch abertos,
                                  CountDownLatch liberar, CountDownLatch terminou) {
            LlmCallScope.openRequest(req, null, null);
            LlmCallScope.openAttempt(1, "v", "h", "s");
            abertos.countDown();
            try {
                liberar.await();
                visto.set(LlmCallScope.current().orElseThrow().generationRequestId());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                LlmCallScope.closeRequest();
                terminou.countDown();
            }
        }
    }
}
