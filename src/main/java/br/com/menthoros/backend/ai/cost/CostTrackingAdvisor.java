package br.com.menthoros.backend.ai.cost;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.core.Ordered;

import java.math.BigDecimal;

/**
 * Advisor de observabilidade de custo LLM: extrai o usage de cada resposta e
 * publica métricas Micrometer com tags {@code model} e {@code route}.
 *
 * Métricas (contadores):
 *   llm.tokens.input       — tokens de entrada NÃO cacheados
 *   llm.tokens.output      — tokens de saída
 *   llm.cache.read.tokens  — tokens lidos do prompt cache
 *   llm.cache.write.tokens — tokens escritos no cache (Anthropic)
 *   llm.cost.estimated.usd — custo estimado via {@link LlmPricingRegistry}
 *
 * {@code llm.tokens.input} exclui os tokens cacheados em ambos os provedores
 * (a OpenAI os inclui em promptTokens; a Anthropic já os reporta separados),
 * para que {@code cache_hit_rate = cache_read / (input + cache_read)} seja
 * comparável entre provedores — insumo da decisão de TTL Anthropic.
 *
 * Best-effort: qualquer falha na extração é engolida (mesmo contrato do
 * {@code LlmUsageLogger}) — a chamada ao LLM nunca depende da instrumentação.
 */
@Slf4j
public final class CostTrackingAdvisor implements CallAdvisor {

    private static final BigDecimal MTOK = BigDecimal.valueOf(1_000_000);

    private final String rota;
    private final LlmPricingRegistry pricing;
    private final MeterRegistry meterRegistry;

    private CostTrackingAdvisor(String rota, LlmPricingRegistry pricing, MeterRegistry meterRegistry) {
        this.rota = rota;
        this.pricing = pricing;
        this.meterRegistry = meterRegistry;
    }

    public static CostTrackingAdvisor paraRota(String rota, LlmPricingRegistry pricing, MeterRegistry meterRegistry) {
        return new CostTrackingAdvisor(rota, pricing, meterRegistry);
    }

    @Override
    public String getName() {
        return "costTracking-" + rota;
    }

    @Override
    public int getOrder() {
        // Perto do fim da cadeia (mede o custo real da chamada), mas antes do
        // advisor terminal interno do Spring AI (LOWEST_PRECEDENCE), que chama
        // o modelo e não propaga para advisors empatados depois dele.
        return Ordered.LOWEST_PRECEDENCE - 1000;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        ChatClientResponse response = chain.nextCall(request);
        registrar(response);
        return response;
    }

    private record TokensLlm(long input, long output, long cacheRead, long cacheWrite) {
    }

    private void registrar(ChatClientResponse response) {
        try {
            if (response == null || response.chatResponse() == null) {
                return;
            }
            ChatResponseMetadata metadata = response.chatResponse().getMetadata();
            if (metadata == null || metadata.getUsage() == null) {
                return;
            }
            TokensLlm tokens = extrairTokens(metadata.getUsage());
            String model = metadata.getModel() == null || metadata.getModel().isBlank()
                    ? "desconhecido" : metadata.getModel();

            incrementar("llm.tokens.input", model, tokens.input());
            incrementar("llm.tokens.output", model, tokens.output());
            incrementar("llm.cache.read.tokens", model, tokens.cacheRead());
            incrementar("llm.cache.write.tokens", model, tokens.cacheWrite());

            pricing.precoDe(model).ifPresentOrElse(
                    preco -> incrementar("llm.cost.estimated.usd", model, custoUsd(tokens, preco).doubleValue()),
                    () -> log.warn("[llm-cost] modelo '{}' sem preço em llm-pricing.yml — custo não registrado (rota {})",
                            model, rota));
        } catch (Exception e) {
            log.warn("[llm-cost] falha ao registrar métricas de custo (ignorado): {}", e.getMessage());
        }
    }

    private TokensLlm extrairTokens(Usage usage) {
        long prompt = orZero(usage.getPromptTokens());
        long completion = orZero(usage.getCompletionTokens());
        Object nativo = usage.getNativeUsage();

        if (nativo instanceof AnthropicApi.Usage anthropic) {
            return new TokensLlm(
                    orZero(anthropic.inputTokens()),
                    orZero(anthropic.outputTokens()),
                    orZero(anthropic.cacheReadInputTokens()),
                    orZero(anthropic.cacheCreationInputTokens()));
        }
        if (nativo instanceof OpenAiApi.Usage openAi) {
            long cacheRead = openAi.promptTokensDetails() != null
                    ? orZero(openAi.promptTokensDetails().cachedTokens()) : 0;
            return new TokensLlm(prompt - cacheRead, completion, cacheRead, 0);
        }
        return new TokensLlm(prompt, completion, 0, 0);
    }

    private BigDecimal custoUsd(TokensLlm tokens, LlmPricingRegistry.PrecoModelo preco) {
        BigDecimal custo = porMtok(tokens.input(), preco.inputPerMtok())
                .add(porMtok(tokens.cacheRead(), preco.cachedInputPerMtok()))
                .add(porMtok(tokens.output(), preco.outputPerMtok()));
        if (preco.cacheWritePerMtok() != null) {
            custo = custo.add(porMtok(tokens.cacheWrite(), preco.cacheWritePerMtok()));
        }
        return custo;
    }

    private static BigDecimal porMtok(long tokens, BigDecimal precoPorMtok) {
        return precoPorMtok.multiply(BigDecimal.valueOf(tokens)).divide(MTOK);
    }

    private void incrementar(String metrica, String model, double valor) {
        Counter.builder(metrica)
                .tag("model", model)
                .tag("route", rota)
                .register(meterRegistry)
                .increment(valor);
    }

    private static long orZero(Integer valor) {
        return valor != null ? valor : 0L;
    }
}
