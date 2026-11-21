package br.com.menthoros.backend.config.external;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.routing.ModelRouter;
import br.com.menthoros.backend.routing.TaskComplexity;
import br.com.menthoros.backend.services.prompt.EvalJudgeSchemaBuilder;
import br.com.menthoros.backend.services.prompt.LlmJsonSchemaBuilder;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.function.Supplier;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.notMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

/**
 * CA3 com reasoning, pelo payload HTTP: contexto real com {@code temperature 0.7} no default global e
 * a rota {@code plano} no sucessor com {@code reasoning-effort low}. O JSON enviado leva o perfil da
 * rota ({@code model}, {@code reasoning_effort}, {@code verbosity}, {@code max_completion_tokens},
 * {@code response_format}) e nenhum campo de amostragem — a OpenAI responde 400 a eles com reasoning.
 * Complementa {@link LlmPerfilDaRotaPayloadIT}, que cobre a rota clássica; fica em classe própria
 * porque as propriedades da rota mudam o contexto.
 */
class LlmPerfilReasoningPayloadIT extends AbstractIntegrationTest {

    private static final String RESPOSTA_OK = """
            {"id":"c","object":"chat.completion","created":1,"model":"gpt-6-luna-teste",
             "choices":[{"index":0,"message":{"role":"assistant","content":"{}"},"finish_reason":"stop"}],
             "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
            """;

    private static final WireMockServer WIRE_MOCK = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        WIRE_MOCK.start();
        WIRE_MOCK.stubFor(post(urlPathEqualTo("/v1/chat/completions"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody(RESPOSTA_OK)));
    }

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.openai.base-url", WIRE_MOCK::baseUrl);
        registry.add("spring.ai.openai.chat.options.temperature", () -> "0.7");
        registry.add("app.llm.routing.plano.model", () -> "gpt-6-luna");
        registry.add("app.llm.routing.plano.temperature", () -> "");
        registry.add("app.llm.routing.plano.reasoning-effort", () -> "low");
        registry.add("app.llm.routing.plano.verbosity", () -> "low");
    }

    @AfterAll
    static void parar() {
        WIRE_MOCK.stop();
    }

    @Autowired
    private ModelRouter modelRouter;

    @Test
    @DisplayName("produção e candidato (LlmJsonSchemaBuilder): perfil da rota, reasoning sem campos de amostragem")
    void producaoECandidato() {
        verificarPayload(() -> new LlmJsonSchemaBuilder().defaultJsonSchemaOptions());
    }

    @Test
    @DisplayName("produção v2 (LlmJsonSchemaBuilder): perfil da rota, reasoning sem campos de amostragem")
    void producaoV2() {
        verificarPayload(() -> new LlmJsonSchemaBuilder().v2JsonSchemaOptions());
    }

    @Test
    @DisplayName("juiz (EvalJudgeSchemaBuilder): perfil da rota, reasoning sem campos de amostragem")
    void juiz() {
        verificarPayload(() -> new EvalJudgeSchemaBuilder().optionsCompleta());
    }

    private void verificarPayload(Supplier<? extends ChatOptions> opcoes) {
        WIRE_MOCK.resetRequests();

        modelRouter.route(TaskComplexity.PLANO).prompt().system("s").user("u").options(opcoes.get())
                .call().chatResponse();

        WIRE_MOCK.verify(postRequestedFor(urlPathEqualTo("/v1/chat/completions"))
                .withRequestBody(matchingJsonPath("$.model", equalTo("gpt-6-luna")))
                .withRequestBody(matchingJsonPath("$.reasoning_effort", equalTo("low")))
                .withRequestBody(matchingJsonPath("$.verbosity", equalTo("low")))
                .withRequestBody(notMatching(".*\"temperature\".*"))
                .withRequestBody(matchingJsonPath("$.max_completion_tokens"))
                .withRequestBody(matchingJsonPath("$.response_format"))
                .withRequestBody(notMatching(".*\"top_p\".*"))
                .withRequestBody(notMatching(".*\"max_tokens\".*"))
                .withRequestBody(notMatching(".*\"frequency_penalty\".*"))
                .withRequestBody(notMatching(".*\"presence_penalty\".*")));
    }
}
