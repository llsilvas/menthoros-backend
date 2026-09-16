package br.com.menthoros.backend.services.helper;

import org.springframework.ai.chat.metadata.Usage;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Custo em memória do eval set (plan-generation-eval-set, fatia 2) — lê {@code llm-pricing.yml}
 * (mesma fonte única de preços de {@code LlmPricingRegistry}) sem precisar do contexto Spring:
 * o runner de eval roda como teste JUnit puro, sem `LlmRoutingProperties` nem os demais beans que
 * `LlmPricingRegistry` exige. Nunca soma pós-hoc do ledger por {@code route} — cada chamada real
 * calcula seu próprio custo a partir do {@link Usage} da própria resposta (design.md §2).
 *
 * <p>Idempotent: YES — leitura pura do classpath + aritmética. Side Effects: NONE.
 * Tenant-aware: NÃO.
 */
public final class EvalCostCalculator {

    private static final String ARQUIVO_PRECOS = "llm-pricing.yml";

    private EvalCostCalculator() {
    }

    /** Custo em USD da chamada, ou {@code null} se o modelo não tem preço cadastrado. */
    public static java.util.Optional<BigDecimal> custoUsd(String modelo, Usage usage) {
        if (usage == null) {
            return java.util.Optional.empty();
        }
        var preco = precoPorMtok(modelo);
        if (preco.isEmpty()) {
            return java.util.Optional.empty();
        }
        long input = usage.getPromptTokens() != null ? usage.getPromptTokens() : 0;
        long output = usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0;
        var mtok = BigDecimal.valueOf(1_000_000);
        BigDecimal custo = preco.get().inputPerMtok().multiply(BigDecimal.valueOf(input)).divide(mtok, 10, RoundingMode.HALF_UP)
                .add(preco.get().outputPerMtok().multiply(BigDecimal.valueOf(output)).divide(mtok, 10, RoundingMode.HALF_UP));
        return java.util.Optional.of(custo);
    }

    private record PrecoModelo(BigDecimal inputPerMtok, BigDecimal outputPerMtok) {
    }

    @SuppressWarnings("unchecked")
    private static java.util.Optional<PrecoModelo> precoPorMtok(String modelo) {
        if (modelo == null) {
            return java.util.Optional.empty();
        }
        try (InputStream in = new ClassPathResource(ARQUIVO_PRECOS).getInputStream()) {
            Map<String, Object> raiz = new Yaml().load(in);
            Map<String, Object> modelos = (Map<String, Object>) raiz.get("modelos");
            // A OpenAI às vezes devolve o nome versionado (ex. "gpt-4o-2024-08-06") em vez do
            // alias configurado ("gpt-4o") — casa por prefixo quando o exato não bate.
            Map<String, Object> dados = modelos.containsKey(modelo) ? (Map<String, Object>) modelos.get(modelo)
                    : modelos.entrySet().stream()
                            .filter(e -> modelo.startsWith(e.getKey()))
                            .map(Map.Entry::getValue)
                            .map(v -> (Map<String, Object>) v)
                            .findFirst().orElse(null);
            if (dados == null) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(new PrecoModelo(
                    new BigDecimal(dados.get("input-per-mtok").toString()),
                    new BigDecimal(dados.get("output-per-mtok").toString())));
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler " + ARQUIVO_PRECOS, e);
        }
    }
}
