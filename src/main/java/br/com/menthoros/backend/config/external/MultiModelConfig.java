package br.com.menthoros.backend.config.external;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
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
 */
@Configuration
public class MultiModelConfig {

    /**
     * GPT-4o-mini — tradução, extração de dados, tarefas simples.
     * Custo estimado: ~R$ 0,0008/operação.
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
     * Claude Haiku 4 — análises simples, tradução de qualidade, velocidade.
     * Custo estimado: ~R$ 0,025/operação.
     */
    @Bean
    @Qualifier("claudeHaikuClient")
    public ChatClient claudeHaikuClient(AnthropicChatModel anthropicChatModel) {
        return ChatClient.builder(anthropicChatModel)
                .defaultOptions(AnthropicChatOptions.builder()
                        .model("claude-haiku-4-5-20251001")
                        .temperature(0.5)
                        .maxTokens(2000)
                        .build())
                .build();
    }

    /**
     * Claude Sonnet 4 — análise pós-treino com skill, prescrição de treinos.
     * Custo estimado: ~R$ 0,10/operação.
     */
    @Bean
    @Qualifier("claudeSonnetClient")
    public ChatClient claudeSonnetClient(AnthropicChatModel anthropicChatModel) {
        return ChatClient.builder(anthropicChatModel)
                .defaultOptions(AnthropicChatOptions.builder()
                        .model("claude-sonnet-4-6")
                        .temperature(0.7)
                        .maxTokens(4000)
                        .build())
                .build();
    }

    /**
     * GPT-4o — raciocínio profundo, análise de lesões, casos especialistas.
     * Custo estimado: ~R$ 0,08/operação.
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
}
