package br.com.menthoros.backend.ai.cost;

import br.com.menthoros.backend.ai.ledger.LlmCallContext;
import br.com.menthoros.backend.ai.ledger.LlmCallRegistro;
import br.com.menthoros.backend.ai.ledger.LlmCallResult;
import br.com.menthoros.backend.ai.ledger.LlmCallScope;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.services.helper.LlmCallLedger;
import org.jspecify.annotations.Nullable;
import java.util.Optional;
import java.util.UUID;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
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
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeUnit;

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
 *   llm.timeout            — chamadas que estouraram o teto da rota
 *
 * Métrica de latência (timer):
 *   llm.call.duration      — duração da chamada, por rota (ADR-0008). Não existia
 *                            instrumentação de latência: os timeouts por rota foram
 *                            derivados de max-tokens, e é este Timer que permite
 *                            recalibrá-los com dado real.
 *
 * {@code llm.tokens.input} exclui os tokens cacheados em ambos os provedores
 * (a OpenAI os inclui em promptTokens; a Anthropic já os reporta separados),
 * para que {@code cache_hit_rate = cache_read / (input + cache_read)} seja
 * comparável entre provedores — insumo da decisão de TTL Anthropic.
 *
 * Best-effort: qualquer falha na extração é engolida (mesmo contrato do
 * {@code LlmUsageLogger}) — a chamada ao LLM nunca depende da instrumentação.
 *
 * <p><b>Ledger</b> (add-plan-generation-ledger, D3/D6/D11): além das métricas, cada chamada vira
 * uma linha em {@code tb_llm_call} via {@link LlmCallLedger} — genérica em toda rota, enriquecida
 * quando o {@link LlmCallScope} da rota {@code plano} está aberto (aí a linha nasce {@code PENDING}
 * e a rota a fecha após validar). No caminho de exceção grava {@code TIMEOUT}/{@code LLM_ERROR}.
 * O counter de custo leva sempre a tag {@code tenant} (sentinela {@code none}): tag condicional
 * quebraria o registry, como o Javadoc de {@link #registrarDuracao} já explica para {@code model}.
 */
@Slf4j
public final class CostTrackingAdvisor implements CallAdvisor {

    private static final BigDecimal MTOK = BigDecimal.valueOf(1_000_000);

    static final String TENANT_AUSENTE = "none";
    private static final String MODELO_DESCONHECIDO = "desconhecido";

    private final String rota;
    private final LlmPricingRegistry pricing;
    private final MeterRegistry meterRegistry;
    private final LlmCallLedger ledger;

    private CostTrackingAdvisor(String rota, LlmPricingRegistry pricing, MeterRegistry meterRegistry,
                                LlmCallLedger ledger) {
        this.rota = rota;
        this.pricing = pricing;
        this.meterRegistry = meterRegistry;
        this.ledger = ledger;
    }

    public static CostTrackingAdvisor paraRota(String rota, LlmPricingRegistry pricing, MeterRegistry meterRegistry,
                                               LlmCallLedger ledger) {
        return new CostTrackingAdvisor(rota, pricing, meterRegistry, ledger);
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
        long inicioNanos = System.nanoTime();
        try {
            ChatClientResponse response = chain.nextCall(request);
            Medicao medicao = registrar(response);
            gravarNoLedger(medicao, response, latenciaMs(inicioNanos), null);
            return response;
        } catch (RuntimeException e) {
            boolean timeout = ehTimeout(e);
            if (timeout) {
                Counter.builder("llm.timeout").tag("route", rota).register(meterRegistry).increment();
            }
            gravarNoLedger(null, null, latenciaMs(inicioNanos), timeout ? LlmCallResult.TIMEOUT : LlmCallResult.LLM_ERROR);
            throw e;
        } finally {
            // Também no caminho de falha: uma chamada que estourou o teto é
            // justamente a que interessa medir para recalibrar o timeout da rota.
            registrarDuracao(System.nanoTime() - inicioNanos);
        }
    }

    /**
     * Só a rota entra como tag: no caminho de exceção não há {@code model} na
     * resposta, e variar o conjunto de tags do mesmo meter quebraria o registry.
     */
    private void registrarDuracao(long duracaoNanos) {
        try {
            Timer.builder("llm.call.duration")
                    .tag("route", rota)
                    .register(meterRegistry)
                    .record(duracaoNanos, TimeUnit.NANOSECONDS);
        } catch (Exception e) {
            log.warn("[llm-cost] falha ao registrar duração (ignorado): {}", e.getMessage());
        }
    }

    /**
     * O teto por rota chega como {@link ResourceAccessException} vinda do
     * {@code RestClient}; o {@code SocketTimeoutException} na causa separa o
     * estouro de teto de outras falhas de transporte (ex.: conexão recusada).
     */
    private static boolean ehTimeout(Throwable e) {
        for (Throwable atual = e; atual != null; atual = atual.getCause()) {
            if (atual instanceof SocketTimeoutException) {
                return true;
            }
            if (atual.getCause() == atual) {
                break;
            }
        }
        return false;
    }

    private record TokensLlm(long input, long output, long cacheRead, long cacheWrite) {
    }

    /** O que as métricas mediram; reaproveitado pelo ledger para a linha usar os mesmos números. */
    private record Medicao(String model, @Nullable TokensLlm tokens, @Nullable BigDecimal custoUsd) {
    }

    private @Nullable Medicao registrar(ChatClientResponse response) {
        try {
            if (response == null || response.chatResponse() == null) {
                return null;
            }
            ChatResponseMetadata metadata = response.chatResponse().getMetadata();
            String model = metadata == null || metadata.getModel() == null || metadata.getModel().isBlank()
                    ? MODELO_DESCONHECIDO : metadata.getModel();
            if (metadata == null || metadata.getUsage() == null) {
                return new Medicao(model, null, null);
            }
            TokensLlm tokens = extrairTokens(metadata.getUsage());

            incrementar("llm.tokens.input", model, tokens.input());
            incrementar("llm.tokens.output", model, tokens.output());
            incrementar("llm.cache.read.tokens", model, tokens.cacheRead());
            incrementar("llm.cache.write.tokens", model, tokens.cacheWrite());

            Optional<BigDecimal> custo = pricing.precoDe(model).map(preco -> custoUsd(tokens, preco));
            custo.ifPresentOrElse(
                    valor -> incrementarCusto(model, valor.doubleValue()),
                    () -> log.warn("[llm-cost] modelo '{}' sem preço em llm-pricing.yml — custo não registrado (rota {})",
                            model, rota));
            return new Medicao(model, tokens, custo.orElse(null));
        } catch (Exception e) {
            log.warn("[llm-cost] falha ao registrar métricas de custo (ignorado): {}", e.getMessage());
            return null;
        }
    }

    /**
     * Grava a linha do ledger. {@code resultadoForcado} vem do caminho de exceção (a linha já nasce
     * terminal — {@code LLM_ERROR}/{@code TIMEOUT}); no caminho feliz a rota {@code plano} (escopo
     * aberto) nasce {@code PENDING} e as demais {@code SUCCESS}. O texto bruto só é lido com escopo
     * — é dado sensível e só a rota {@code plano} tem uso para ele. Best-effort: nada aqui pode
     * derrubar a chamada.
     *
     * <p><b>{@code registerCallId} só no caminho feliz (bug real, achado no `/qa` de
     * add-plan-generation-ledger):</b> {@code br.com.menthoros.backend.services.helper.PlanoLlmLedgerHook.Sessao#chamar} usa
     * {@code LlmCallScope.lastCallId()} para distinguir "chamada aceita, conversão falhou depois"
     * (id presente → {@code PARSE_ERROR}) de "provider já terminalizou a linha" (id ausente → nada
     * a fazer). Registrar o id também no caminho de exceção fazia o hook sobrescrever toda linha
     * {@code LLM_ERROR}/{@code TIMEOUT} para {@code PARSE_ERROR} — nenhuma chamada da rota
     * {@code plano} jamais persistia como erro de provider.
     */
    private void gravarNoLedger(@Nullable Medicao medicao, @Nullable ChatClientResponse response,
                                int latenciaMs, @Nullable LlmCallResult resultadoForcado) {
        try {
            Optional<LlmCallContext> contexto = LlmCallScope.current();
            LlmCallResult resultado = resultadoForcado != null ? resultadoForcado
                    : contexto.isPresent() ? LlmCallResult.PENDING : LlmCallResult.SUCCESS;
            TokensLlm tokens = medicao != null ? medicao.tokens() : null;
            LlmCallRegistro registro = new LlmCallRegistro(
                    rota,
                    medicao != null ? medicao.model() : MODELO_DESCONHECIDO,
                    tokens != null ? tokens.input() : null,
                    tokens != null ? tokens.output() : null,
                    tokens != null ? tokens.cacheRead() : null,
                    tokens != null ? tokens.cacheWrite() : null,
                    medicao != null ? medicao.custoUsd() : null,
                    latenciaMs,
                    resultado,
                    TenantContext.getTenantId(),
                    LlmCallScope.transportRetries().orElse(null),
                    contexto.isPresent() ? textoDaResposta(response) : null,
                    contexto);
            Optional<UUID> callId = ledger.registrarChamada(registro);
            if (resultadoForcado == null) {
                LlmCallScope.registerCallId(callId.orElse(null));
            }
        } catch (Exception e) {
            log.warn("[llm-ledger] falha ao montar a linha da rota {} (ignorado): {}", rota, e.getMessage());
        }
    }

    private static @Nullable String textoDaResposta(@Nullable ChatClientResponse response) {
        if (response == null || response.chatResponse() == null || response.chatResponse().getResult() == null
                || response.chatResponse().getResult().getOutput() == null) {
            return null;
        }
        return response.chatResponse().getResult().getOutput().getText();
    }

    private static int latenciaMs(long inicioNanos) {
        return (int) Math.min(Integer.MAX_VALUE, (System.nanoTime() - inicioNanos) / 1_000_000L);
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
        // 10 casas: preserva precisão de custos sub-centavo por chamada
        return precoPorMtok.multiply(BigDecimal.valueOf(tokens))
                .divide(MTOK, 10, java.math.RoundingMode.HALF_UP);
    }

    /** Custo leva sempre {@code tenant} (sentinela {@code none}) — tag condicional quebraria o registry. */
    private void incrementarCusto(String model, double valor) {
        UUID tenant = TenantContext.getTenantId();
        Counter.builder("llm.cost.estimated.usd")
                .tag("model", model)
                .tag("route", rota)
                .tag("tenant", tenant != null ? tenant.toString() : TENANT_AUSENTE)
                .register(meterRegistry)
                .increment(valor);
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
