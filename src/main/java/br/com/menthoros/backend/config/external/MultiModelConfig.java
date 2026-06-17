package br.com.menthoros.backend.config.external;

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
 * Configura os 4 beans ChatClient nomeados para roteamento multi-modelo.
 *
 * Cada bean injeta o ChatModel correto para seu provider, evitando que
 * o Spring AI use o builder do provider errado quando dois starters
 * (openai + anthropic) estão ativos.
 *
 * Nenhum bean aqui tem @Primary — o bean primário está em ChatClientConfig.
 * Injeção: use @Qualifier("gpt4oMiniClient"), ("claudeHaikuClient"),
 *          ("claudeSonnetClient") ou ("gpt4oClient").
 *
 * Preços vigentes (maio/2026, USD; ~R$5,70/USD):
 *   gpt-4o-mini:       $0,15/MTok input | $0,60/MTok output
 *   Claude Haiku 4.5:  $1,00/MTok input | $5,00/MTok output
 *   Claude Sonnet 4.6: $3,00/MTok input | $15,00/MTok output
 *   gpt-4o:            $2,50/MTok input | $10,00/MTok output
 *
 * Alternativas mais baratas disponíveis na OpenAI:
 *   gpt-4.1-nano: $0,10/MTok input | $0,40/MTok output (ultra-baixo custo)
 *   gpt-4.1-mini: $0,40/MTok input | $1,60/MTok output (melhor custo/qualidade)
 */
@Configuration
public class MultiModelConfig {

    /**
     * GPT-4o-mini — tradução, extração de dados, tarefas simples.
     * Preço: $0,15/MTok input | $0,60/MTok output.
     * Custo estimado: ~R$ 0,001/operação (300 tokens input + 200 output).
     */
    @Bean
    @Qualifier("gpt4oMiniClient")
    public ChatClient gpt4oMiniClient(OpenAiChatModel openAiChatModel) {
        return ChatClient.builder(openAiChatModel)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model("gpt-4o-mini")
                        .temperature(0.3)
                        .maxTokens(1000)
                        .build())
                .build();
    }

    /**
     * Claude Haiku 4.5 — análises simples, tradução de qualidade, velocidade.
     * Preço: $1,00/MTok input | $5,00/MTok output | cache hit: $0,10/MTok.
     * Custo estimado: ~R$ 0,014/operação sem cache; ~R$ 0,004 com cache hit.
     */
    @Bean
    @Qualifier("claudeHaikuClient")
    public ChatClient claudeHaikuClient(AnthropicChatModel anthropicChatModel) {
        return ChatClient.builder(anthropicChatModel)
                .defaultOptions(AnthropicChatOptions.builder()
                        .model("claude-haiku-4-5-20251001")
                        .temperature(0.5)
                        .maxTokens(2000)
                        .cacheOptions(AnthropicCacheOptions.builder()
                                .strategy(AnthropicCacheStrategy.SYSTEM_ONLY)
                                .messageTypeTtl(MessageType.SYSTEM, AnthropicCacheTtl.ONE_HOUR)
                                .build())
                        .build())
                .build();
    }

    /**
     * Claude Sonnet 4.6 — análise pós-treino com skill, prescrição de treinos.
     * Preço: $3,00/MTok input | $15,00/MTok output | cache hit: $0,30/MTok.
     * Custo estimado: ~R$ 0,12/operação sem cache; ~R$ 0,033 com cache hit no SKILL.md.
     */
    @Bean
    @Qualifier("claudeSonnetClient")
    public ChatClient claudeSonnetClient(AnthropicChatModel anthropicChatModel) {
        return ChatClient.builder(anthropicChatModel)
                .defaultOptions(AnthropicChatOptions.builder()
                        .model("claude-sonnet-4-6")
                        .temperature(0.7)
                        .maxTokens(4000)
                        .cacheOptions(AnthropicCacheOptions.builder()
                                .strategy(AnthropicCacheStrategy.SYSTEM_ONLY)
                                .messageTypeTtl(MessageType.SYSTEM, AnthropicCacheTtl.ONE_HOUR)
                                .build())
                        .build())
                .build();
    }

    /**
     * GPT-4o — raciocínio profundo, análise de lesões, casos especialistas.
     * Preço: $2,50/MTok input | $10,00/MTok output.
     * Custo estimado: ~R$ 0,11/operação (2000 tokens input + 1500 output).
     */
    @Bean
    @Qualifier("gpt4oClient")
    public ChatClient gpt4oClient(OpenAiChatModel openAiChatModel) {
        return ChatClient.builder(openAiChatModel)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model("gpt-4o")
                        .temperature(0.8)
                        .maxTokens(8000)
                        .build())
                .build();
    }

    /**
     * GPT-4o (plano semanal) — geração de plano, o fluxo mais caro e crítico do sistema.
     * Bean dedicado para tornar o modelo explícito e rastreável por feature (antes usava o
     * ChatClient @Primary genérico). Mantém gpt-4o até decisão de migrar para outro modelo.
     * Preço: $2,50/MTok input | $10,00/MTok output.
     * Custo estimado: ~R$ 0,17/operação (3000 tokens input + 6000 output).
     */
    @Bean
    @Qualifier("gpt4oPlanoClient")
    public ChatClient gpt4oPlanoClient(OpenAiChatModel openAiChatModel) {
        return ChatClient.builder(openAiChatModel)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model("gpt-4o")
                        .temperature(0.7)
                        .maxTokens(6000)
                        .build())
                .build();
    }
}
