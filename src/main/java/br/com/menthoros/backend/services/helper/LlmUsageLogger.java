package br.com.menthoros.backend.services.helper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;

/**
 * Loga o consumo de tokens do LLM na geração de plano — em especial os
 * {@code cached_tokens} da OpenAI (prompt caching automático por prefixo), para
 * medir se o bloco estático do prompt já é cacheado.
 *
 * <p>Best-effort e não intrusivo: qualquer falha na extração/log é engolida —
 * a geração do plano nunca depende desta instrumentação.
 */
@Slf4j
@Component
public class LlmUsageLogger {

    /**
     * Idempotent: YES — apenas lê metadata e loga.
     * Side Effects: log (INFO). Nunca lança.
     * Tenant-aware: NO — observabilidade técnica.
     */
    public void registrar(ChatResponse response) {
        try {
            if (response == null || response.getMetadata() == null) {
                return;
            }
            Usage usage = response.getMetadata().getUsage();
            if (usage == null) {
                return;
            }
            int prompt = orZero(usage.getPromptTokens());
            int completion = orZero(usage.getCompletionTokens());
            int cached = orZero(extrairCachedTokens(usage.getNativeUsage()));
            String cacheHitRatio = prompt > 0 ? "%.2f".formatted((double) cached / prompt) : "0.00";

            log.info("[llm-usage] plano: promptTokens={} cachedTokens={} completionTokens={} cacheHitRatio={}",
                    prompt, cached, completion, cacheHitRatio);
        } catch (Exception e) {
            log.warn("[llm-usage] falha ao registrar o uso de tokens (ignorado): {}", e.getMessage());
        }
    }

    /**
     * Extrai os {@code cached_tokens} do usage nativo da OpenAI. Provider-specific:
     * retorna {@code null} para outros provedores ou quando o campo está ausente.
     */
    static Integer extrairCachedTokens(Object nativeUsage) {
        if (nativeUsage instanceof OpenAiApi.Usage openAiUsage) {
            OpenAiApi.Usage.PromptTokensDetails detalhes = openAiUsage.promptTokensDetails();
            if (detalhes != null) {
                return detalhes.cachedTokens();
            }
        }
        return null;
    }

    private static int orZero(Integer i) {
        return i != null ? i : 0;
    }
}
