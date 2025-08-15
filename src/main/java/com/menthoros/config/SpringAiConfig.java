package com.menthoros.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class SpringAiConfig {

    @Value("${spring.ai.openai.chat.options.model:gpt-4o}")
    private String defaultModel;

    @Bean
    @Primary
    public ChatClient menthorosChatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem("""
                    Você é um treinador de corrida especialista em gerar planos de treino seguros e personalizados.
                    Sempre responda com JSON válido seguindo exatamente a estrutura solicitada.
                    Priorize a segurança do atleta acima de tudo.
                    """)
                .defaultAdvisors(
                    // Adiciona memória para contexto de conversação (útil para ajustes)
                    new MessageChatMemoryAdvisor(new InMemoryChatMemory())
                )
                .build();
    }

    @Bean("structuredChatClient")
    public ChatClient structuredChatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem("""
                    Você é um especialista em treinamento de corrida.
                    CRÍTICO: Responda APENAS com JSON válido, sem texto adicional.
                    NUNCA inclua markdown, explicações ou comentários.
                    """)
                .build();
    }

    @Bean("embeddingChatClient") 
    public ChatClient embeddingChatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem("""
                    Você analisa dados de treino para gerar insights e contexto.
                    Forneça respostas concisas e focadas em dados objetivos.
                    """)
                .build();
    }
}