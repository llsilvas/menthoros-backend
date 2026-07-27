package br.com.menthoros.backend.config.external;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiApi;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Prova o CA1 e o CA2 da change {@code add-external-call-resilience}: o teto de
 * duração é aplicado por rota, e não por provider.
 *
 * <p>A asserção que importa é a do {@link PorRota}: {@code simple} e {@code plano}
 * são servidos pelo <b>mesmo</b> provider (OpenAI) apontando para o mesmo WireMock,
 * com o mesmo atraso — e ainda assim uma estoura e a outra não. Um timeout no nível
 * do provider não conseguiria produzir esse resultado.
 *
 * <p>Sem contexto Spring: monta o {@link OpenAiChatModel} base direto contra o
 * WireMock, como {@code IntervalsIcuTimeoutTest} faz com o WebClient real.
 */
class MultiModelTimeoutTest {

    private static final long ATRASO_MS = 1_500;

    private static final String RESPOSTA_OK = """
            {
              "id": "chatcmpl-teste",
              "object": "chat.completion",
              "created": 1,
              "model": "gpt-4o",
              "choices": [
                {
                  "index": 0,
                  "message": { "role": "assistant", "content": "ok" },
                  "finish_reason": "stop"
                }
              ],
              "usage": { "prompt_tokens": 1, "completion_tokens": 1, "total_tokens": 2 }
            }
            """;

    private WireMockServer wireMock;
    private OpenAiApi apiBase;
    private OpenAiChatModel modelBase;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
        wireMock.stubFor(post(urlPathEqualTo("/v1/chat/completions"))
                .willReturn(aResponse()
                        .withFixedDelay((int) ATRASO_MS)
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(RESPOSTA_OK)));

        apiBase = OpenAiApi.builder()
                .baseUrl(wireMock.baseUrl())
                .apiKey("chave-timeout-test")
                .build();
        modelBase = OpenAiChatModel.builder().openAiApi(apiBase).build();
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Nested
    @DisplayName("modeloOpenAiComTimeout")
    class PorRota {

        @Test
        @DisplayName("rota com teto abaixo do atraso falha dentro do teto e libera a thread")
        void rotaCurtaEstoura() {
            OpenAiChatModel curta = MultiModelConfig.modeloOpenAiComTimeout(
                    modelBase, apiBase, rota(Duration.ofMillis(300)));

            // Se o teto não fosse aplicado, a chamada só voltaria após ATRASO_MS.
            assertTimeoutPreemptively(Duration.ofMillis(ATRASO_MS - 200), () ->
                    assertThatThrownBy(() -> curta.call("oi")).isNotNull());
        }

        @Test
        @DisplayName("rota com teto acima do atraso completa normalmente")
        void rotaLongaCompleta() {
            OpenAiChatModel longa = MultiModelConfig.modeloOpenAiComTimeout(
                    modelBase, apiBase, rota(Duration.ofSeconds(10)));

            assertThat(longa.call("oi")).isEqualTo("ok");
        }

        @Test
        @DisplayName("duas rotas do mesmo provider respeitam tetos distintos (CA2)")
        void tetosDistintosNoMesmoProvider() {
            OpenAiChatModel simple = MultiModelConfig.modeloOpenAiComTimeout(
                    modelBase, apiBase, rota(Duration.ofMillis(300)));
            OpenAiChatModel plano = MultiModelConfig.modeloOpenAiComTimeout(
                    modelBase, apiBase, rota(Duration.ofSeconds(10)));

            assertThatThrownBy(() -> simple.call("oi")).isNotNull();
            assertThat(plano.call("oi")).isEqualTo("ok");
        }
    }

    private LlmRoutingProperties.RotaLlm rota(Duration timeout) {
        LlmRoutingProperties.RotaLlm rota = new LlmRoutingProperties.RotaLlm();
        rota.setModel("gpt-4o");
        rota.setTemperature(0.2);
        rota.setMaxTokens(1000);
        rota.setTimeout(timeout);
        return rota;
    }
}
