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

    private final ChatClient simpleClient;
    private final ChatClient claudeHaikuClient;
    private final ChatClient claudeSonnetClient;
    private final ChatClient expertClient;
    private final ChatClient planoClient;

    public ModelRouter(
            @Qualifier("simpleClient") ChatClient simpleClient,
            @Qualifier("claudeHaikuClient") ChatClient claudeHaikuClient,
            @Qualifier("claudeSonnetClient") ChatClient claudeSonnetClient,
            @Qualifier("expertClient") ChatClient expertClient,
            @Qualifier("planoClient") ChatClient planoClient) {
        this.simpleClient = simpleClient;
        this.claudeHaikuClient = claudeHaikuClient;
        this.claudeSonnetClient = claudeSonnetClient;
        this.expertClient = expertClient;
        this.planoClient = planoClient;
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
            case SIMPLE -> simpleClient;
            case STANDARD -> claudeHaikuClient;
            case COMPLEX -> claudeSonnetClient;
            case EXPERT -> expertClient;
            case PLANO -> planoClient;
        };
    }
}
