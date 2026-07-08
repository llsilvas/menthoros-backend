package br.com.menthoros.backend.config.external;

import br.com.menthoros.backend.ai.cost.CostTrackingAdvisor;
import br.com.menthoros.backend.ai.cost.LlmPricingRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicCacheOptions;
import org.springframework.ai.anthropic.api.AnthropicCacheStrategy;
import org.springframework.ai.anthropic.api.AnthropicCacheTtl;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
    public ChatClient gpt4oMiniClient(OpenAiChatModel openAiChatModel) {
        return ChatClient.builder(openAiChatModel)
                .defaultOptions(opcoesOpenAi(props.getSimple()))
                .defaultAdvisors(advisorDeCusto("simple"))
                .build();
    }

    /**
     * Rota {@code standard} — análises simples, tradução de qualidade, velocidade.
     */
    @Bean
    @Qualifier("claudeHaikuClient")
    public ChatClient claudeHaikuClient(AnthropicChatModel anthropicChatModel) {
        return ChatClient.builder(anthropicChatModel)
                .defaultOptions(opcoesAnthropic(props.getStandard()))
                .defaultAdvisors(advisorDeCusto("standard"))
                .build();
    }

    /**
     * Rota {@code complex} — análise pós-treino com skill, prescrição de treinos.
     */
    @Bean
    @Qualifier("claudeSonnetClient")
    public ChatClient claudeSonnetClient(AnthropicChatModel anthropicChatModel) {
        return ChatClient.builder(anthropicChatModel)
                .defaultOptions(opcoesAnthropic(props.getComplex()))
                .defaultAdvisors(advisorDeCusto("complex"))
                .build();
    }

    /**
     * Rota {@code expert} — raciocínio profundo, análise de lesões, casos especialistas.
     */
    @Bean
    @Qualifier("gpt4oClient")
    public ChatClient gpt4oClient(OpenAiChatModel openAiChatModel) {
        return ChatClient.builder(openAiChatModel)
                .defaultOptions(opcoesOpenAi(props.getExpert()))
                .defaultAdvisors(advisorDeCusto("expert"))
                .build();
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
    public ChatClient gpt4oPlanoClient(OpenAiChatModel openAiChatModel) {
        return ChatClient.builder(openAiChatModel)
                .defaultOptions(opcoesOpenAi(props.getPlano()))
                .defaultAdvisors(advisorDeCusto("plano"))
                .build();
    }

    private CostTrackingAdvisor advisorDeCusto(String rota) {
        return CostTrackingAdvisor.paraRota(rota, pricingRegistry, meterRegistry);
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
