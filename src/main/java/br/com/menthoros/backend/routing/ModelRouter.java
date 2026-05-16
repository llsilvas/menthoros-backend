package br.com.menthoros.backend.routing;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Roteia chamadas de IA para o ChatClient correto com base na complexidade da tarefa.
 *
 * Idempotent: YES — Determinístico, sem estado.
 * Side Effects: NONE
 * Tenant-aware: NO
 */
@Component
public class ModelRouter {

    private final ChatClient gpt4oMiniClient;
    private final ChatClient claudeHaikuClient;
    private final ChatClient claudeSonnetClient;
    private final ChatClient gpt4oClient;

    public ModelRouter(
            @Qualifier("gpt4oMiniClient") ChatClient gpt4oMiniClient,
            @Qualifier("claudeHaikuClient") ChatClient claudeHaikuClient,
            @Qualifier("claudeSonnetClient") ChatClient claudeSonnetClient,
            @Qualifier("gpt4oClient") ChatClient gpt4oClient) {
        this.gpt4oMiniClient = gpt4oMiniClient;
        this.claudeHaikuClient = claudeHaikuClient;
        this.claudeSonnetClient = claudeSonnetClient;
        this.gpt4oClient = gpt4oClient;
    }

    /**
     * Retorna o ChatClient adequado para a complexidade informada.
     *
     * Idempotent: YES
     * Side Effects: NONE
     * Tenant-aware: NO
     *
     * @param complexity nível de complexidade da tarefa
     * @return ChatClient configurado para o modelo correspondente
     * @throws IllegalArgumentException se complexity for null
     */
    public ChatClient route(TaskComplexity complexity) {
        if (complexity == null) {
            throw new IllegalArgumentException("TaskComplexity não pode ser null");
        }
        return switch (complexity) {
            case SIMPLE -> gpt4oMiniClient;
            case STANDARD -> claudeHaikuClient;
            case COMPLEX -> claudeSonnetClient;
            case EXPERT -> gpt4oClient;
        };
    }
}
