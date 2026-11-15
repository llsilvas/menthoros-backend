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
 * CA3 de migrate-openai-retired-models, pelo payload HTTP: contexto real, com {@code temperature 0.7}
 * deixada de propósito no default global. O JSON que chega ao provider tem de levar o perfil da rota
 * {@code plano} ({@code temperature 0.2}, {@code max_completion_tokens}) e nada do default global
 * ({@code top_p}, penalidades, {@code max_tokens} legado), nos três formatos de opção que os chamadores
 * passam por requisição (produção v1/v2 via {@link LlmJsonSchemaBuilder}, juiz via
 * {@link EvalJudgeSchemaBuilder}) — todos chamam {@code .options(schema)} sobre o mesmo
 * {@code ChatClient} da rota.
 *
 * <p>TODO (task 4): quando o sucessor tiver preço em {@code llm-pricing.yml}, configurar a rota com
 * {@code reasoning-effort} e assertar também {@code reasoning_effort} e a ausência de {@code temperature}.
 * Hoje o {@code LlmPricingRegistry} recusa subir com modelo sem preço.
 */
class LlmPerfilDaRotaPayloadIT extends AbstractIntegrationTest {

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
    }

    @AfterAll
    static void parar() {
        WIRE_MOCK.stop();
    }

    @Autowired
    private ModelRouter modelRouter;

    @Test
    @DisplayName("produção e candidato (LlmJsonSchemaBuilder): perfil da rota, perfil da rota vence o default global")
    void producaoECandidato() {
        verificarPayload(() -> new LlmJsonSchemaBuilder().defaultJsonSchemaOptions());
    }

    @Test
    @DisplayName("produção v2 (LlmJsonSchemaBuilder): perfil da rota, perfil da rota vence o default global")
    void producaoV2() {
        verificarPayload(() -> new LlmJsonSchemaBuilder().v2JsonSchemaOptions());
    }

    @Test
    @DisplayName("juiz (EvalJudgeSchemaBuilder): perfil da rota, perfil da rota vence o default global")
    void juiz() {
        verificarPayload(() -> new EvalJudgeSchemaBuilder().optionsCompleta());
    }

    private void verificarPayload(Supplier<? extends ChatOptions> opcoes) {
        WIRE_MOCK.resetRequests();

        modelRouter.route(TaskComplexity.PLANO).prompt().system("s").user("u").options(opcoes.get())
                .call().chatResponse();

        WIRE_MOCK.verify(postRequestedFor(urlPathEqualTo("/v1/chat/completions"))
                .withRequestBody(matchingJsonPath("$.model", equalTo("gpt-4o")))
                .withRequestBody(matchingJsonPath("$.temperature", equalTo("0.2")))
                .withRequestBody(matchingJsonPath("$.max_completion_tokens"))
                .withRequestBody(matchingJsonPath("$.response_format"))
                .withRequestBody(notMatching(".*\"top_p\".*"))
                .withRequestBody(notMatching(".*\"max_tokens\".*"))
                .withRequestBody(notMatching(".*\"frequency_penalty\".*"))
                .withRequestBody(notMatching(".*\"presence_penalty\".*")));
    }
}
