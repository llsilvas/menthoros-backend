package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.entity.RevisaoSemanal;
import br.com.menthoros.backend.routing.ModelRouter;
import br.com.menthoros.backend.routing.TaskComplexity;
import br.com.menthoros.backend.services.prompt.PromptTemplateLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

/**
 * Chamada ao modelo para redigir o foco da semana, isolada num bean próprio para que o
 * {@code @Retryable} realmente funcione.
 *
 * <p>Motivo da extração: com a anotação no mesmo método que captura a exceção, nada propaga e o
 * Spring Retry nunca é acionado — o retry vira código morto. Aqui a exceção sobe pelo proxy, as
 * tentativas acontecem, e só a falha final chega ao chamador, que decide manter o template.
 * (Autoinvocação também não passaria pelo proxy, então um método privado não resolveria.)
 */
@Component
@RequiredArgsConstructor
public class WeeklyFocusModelClient {

    private static final String PROMPT_TEMPLATE = "weekly-focus-user-prompt.txt";

    private final ModelRouter modelRouter;
    private final PromptTemplateLoader templateLoader;

    /**
     * Idempotent: YES — mesma revisão produz a mesma requisição (a resposta do modelo varia).
     * Side Effects: External API call (LLM).
     * Tenant-aware: N/A — recebe a revisão já resolvida no tenant.
     *
     * @throws RuntimeException propagada de propósito: é o que dispara o retry
     */
    @Retryable(retryFor = Exception.class, maxAttempts = 2, backoff = @Backoff(delay = 2000))
    public String redigirFoco(RevisaoSemanal revisao) {
        String prompt = templateLoader.loadAndFormat(PROMPT_TEMPLATE, dadosDoPrompt(revisao));
        return modelRouter.route(TaskComplexity.SIMPLE)
                .prompt()
                .user(prompt)
                .call()
                .content();
    }

    /** Só o sinal já consolidado — o LLM redige, não recalcula (non-goal explícito da change). */
    private String dadosDoPrompt(RevisaoSemanal revisao) {
        return """
                {"recommendationType":"%s","adherenceStatus":"%s","completionRate":%s,\
                "sufficientData":%s,"tsbFim":%s}"""
                .formatted(
                        revisao.getRecommendationType(),
                        revisao.getAdherenceStatus(),
                        revisao.getCompletionRate(),
                        revisao.isSufficientData(),
                        revisao.getPlanoSemanal().getTsbFim());
    }
}
