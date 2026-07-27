package br.com.menthoros.backend.config.external;

import br.com.menthoros.backend.ai.cost.CostTrackingAdvisor;
import br.com.menthoros.backend.ai.cost.LlmPricingRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.anthropic.api.AnthropicCacheOptions;
import org.springframework.ai.anthropic.api.AnthropicCacheStrategy;
import org.springframework.ai.anthropic.api.AnthropicCacheTtl;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicConnectionProperties;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Configura os 5 beans ChatClient nomeados para roteamento multi-modelo.
 *
 * Cada bean injeta o ChatModel correto para seu provider, evitando que
 * o Spring AI use o builder do provider errado quando dois starters
 * (openai + anthropic) estão ativos.
 *
 * Model IDs e parâmetros (temperature, maxTokens) vêm de
 * {@link LlmRoutingProperties} ({@code app.llm.routing} no application.yml) —
 * troca de modelo por ambiente sem recompilação. Preços vivem exclusivamente
 * em {@code llm-pricing.yml}.
 *
 * Nenhum bean aqui tem @Primary — o bean primário está em ChatClientConfig.
 * Injeção: use @Qualifier("gpt4oMiniClient"), ("claudeHaikuClient"),
 *          ("claudeSonnetClient"), ("gpt4oClient") ou ("gpt4oPlanoClient").
 */
@Configuration
@RequiredArgsConstructor
public class MultiModelConfig {

    private final LlmRoutingProperties props;
    private final LlmPricingRegistry pricingRegistry;
    private final MeterRegistry meterRegistry;

    /**
     * Rota {@code simple} — tradução, extração de dados, tarefas simples.
     */
    @Bean
    @Qualifier("gpt4oMiniClient")
    public ChatClient gpt4oMiniClient(OpenAiChatModel openAiChatModel, OpenAiApi openAiApi) {
        return clienteDeRota(modeloOpenAiComTimeout(openAiChatModel, openAiApi, props.getSimple()),
                opcoesOpenAi(props.getSimple()), "simple");
    }

    /**
     * Rota {@code standard} — análises simples, tradução de qualidade, velocidade.
     */
    @Bean
    @Qualifier("claudeHaikuClient")
    public ChatClient claudeHaikuClient(AnthropicConnectionProperties conexao,
                                       RetryTemplate retryTemplate,
                                       ToolCallingManager toolCallingManager) {
        return clienteDeRota(modeloAnthropicComTimeout(conexao, retryTemplate, toolCallingManager,
                        props.getStandard()),
                opcoesAnthropic(props.getStandard()), "standard");
    }

    /**
     * Rota {@code complex} — análise pós-treino com skill, prescrição de treinos.
     */
    @Bean
    @Qualifier("claudeSonnetClient")
    public ChatClient claudeSonnetClient(AnthropicConnectionProperties conexao,
                                        RetryTemplate retryTemplate,
                                        ToolCallingManager toolCallingManager) {
        return clienteDeRota(modeloAnthropicComTimeout(conexao, retryTemplate, toolCallingManager,
                        props.getComplex()),
                opcoesAnthropic(props.getComplex()), "complex");
    }

    /**
     * Rota {@code expert} — raciocínio profundo, análise de lesões, casos especialistas.
     */
    @Bean
    @Qualifier("gpt4oClient")
    public ChatClient gpt4oClient(OpenAiChatModel openAiChatModel, OpenAiApi openAiApi) {
        return clienteDeRota(modeloOpenAiComTimeout(openAiChatModel, openAiApi, props.getExpert()),
                opcoesOpenAi(props.getExpert()), "expert");
    }

    /**
     * Rota {@code plano} — geração de plano semanal, o fluxo mais caro e crítico
     * do sistema. Bean dedicado para tornar o modelo explícito e rastreável por
     * feature (antes usava o ChatClient @Primary genérico, que herdava os
     * defaults do application.yml). Temperatura baixa favorece aderência às
     * regras estruturais do plano (ex.: nº de etapas por tipo de treino).
     */
    @Bean
    @Qualifier("gpt4oPlanoClient")
    public ChatClient gpt4oPlanoClient(OpenAiChatModel openAiChatModel, OpenAiApi openAiApi) {
        return clienteDeRota(modeloOpenAiComTimeout(openAiChatModel, openAiApi, props.getPlano()),
                opcoesOpenAi(props.getPlano()), "plano");
    }

    /**
     * Monta o ChatClient de uma rota: opções default + advisor de custo com a tag
     * da rota. Separado da derivação do model para manter uma costura testável sem
     * cliente HTTP — o teste do advisor injeta um ChatModel mockado aqui.
     */
    ChatClient clienteDeRota(org.springframework.ai.chat.model.ChatModel model,
                             org.springframework.ai.chat.prompt.ChatOptions opcoes,
                             String nomeRota) {
        return ChatClient.builder(model)
                .defaultOptions(opcoes)
                .defaultAdvisors(advisorDeCusto(nomeRota))
                .build();
    }

    private CostTrackingAdvisor advisorDeCusto(String rota) {
        return CostTrackingAdvisor.paraRota(rota, pricingRegistry, meterRegistry);
    }

    /**
     * Teto de conexão, igual para todas as rotas: abrir socket não depende do
     * tamanho da resposta. Mesmo valor do Keycloak e do intervals.icu.
     */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    /**
     * Deriva um {@link OpenAiChatModel} com o teto de leitura da rota.
     *
     * Usa {@code mutate()} nos dois níveis para preservar o que o auto-config
     * montou (tool calling, retry template, observação) — só o cliente HTTP é
     * trocado.
     */
    static OpenAiChatModel modeloOpenAiComTimeout(OpenAiChatModel base, OpenAiApi api,
                                                  LlmRoutingProperties.RotaLlm rota) {
        return base.mutate()
                .openAiApi(api.mutate()
                        .restClientBuilder(restClientComTimeout(rota.getTimeout()))
                        .build())
                .build();
    }

    /**
     * Monta um {@link AnthropicChatModel} com o teto de leitura da rota.
     *
     * Diferente do OpenAI, nem {@code AnthropicApi} nem {@code AnthropicChatModel}
     * expõem {@code mutate()} nesta versão do Spring AI, então não há como derivar
     * do bean auto-configurado: a API é remontada a partir das mesmas connection
     * properties que o {@code AnthropicChatAutoConfiguration} usa. Se um upgrade
     * do Spring AI passar a oferecer {@code mutate()}, este método vira o mesmo
     * formato do {@link #modeloOpenAiComTimeout}.
     */
    static AnthropicChatModel modeloAnthropicComTimeout(AnthropicConnectionProperties conexao,
                                                        RetryTemplate retryTemplate,
                                                        ToolCallingManager toolCallingManager,
                                                        LlmRoutingProperties.RotaLlm rota) {
        AnthropicApi api = AnthropicApi.builder()
                .baseUrl(conexao.getBaseUrl())
                .completionsPath(conexao.getCompletionsPath())
                .apiKey(conexao.getApiKey())
                .anthropicVersion(conexao.getVersion())
                .anthropicBetaFeatures(conexao.getBetaVersion())
                .restClientBuilder(restClientComTimeout(rota.getTimeout()))
                .build();

        return AnthropicChatModel.builder()
                .anthropicApi(api)
                .defaultOptions(opcoesAnthropic(rota))
                .toolCallingManager(toolCallingManager)
                .retryTemplate(retryTemplate)
                .build();
    }

    /**
     * Só o timeout de leitura varia por rota — ver {@code LlmRoutingProperties.RotaLlm#timeout}.
     *
     * Cobre apenas o caminho síncrono ({@code RestClient}). O streaming usa
     * {@code WebClient}, que segue sem teto — nenhuma rota do sistema faz streaming hoje.
     */
    private static RestClient.Builder restClientComTimeout(Duration timeoutDeLeitura) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
        factory.setReadTimeout((int) timeoutDeLeitura.toMillis());
        return RestClient.builder().requestFactory(factory);
    }

    static OpenAiChatOptions opcoesOpenAi(LlmRoutingProperties.RotaLlm rota) {
        return OpenAiChatOptions.builder()
                .model(rota.getModel())
                .temperature(rota.getTemperature())
                .maxTokens(rota.getMaxTokens())
                .build();
    }

    static AnthropicChatOptions opcoesAnthropic(LlmRoutingProperties.RotaLlm rota) {
        return AnthropicChatOptions.builder()
                .model(rota.getModel())
                .temperature(rota.getTemperature())
                .maxTokens(rota.getMaxTokens())
                .cacheOptions(AnthropicCacheOptions.builder()
                        .strategy(AnthropicCacheStrategy.SYSTEM_ONLY)
                        .messageTypeTtl(MessageType.SYSTEM, AnthropicCacheTtl.ONE_HOUR)
                        .build())
                .build();
    }
}
