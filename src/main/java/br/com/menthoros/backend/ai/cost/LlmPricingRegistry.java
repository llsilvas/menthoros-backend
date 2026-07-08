package br.com.menthoros.backend.ai.cost;

import br.com.menthoros.backend.config.external.LlmRoutingProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Fonte única de preços LLM (RISK-02): carrega {@code llm-pricing.yml} do
 * classpath no startup e expõe o preço por model ID. Preços não vivem em
 * Javadoc nem em código — só no yml.
 *
 * Falha rápido no startup se algum modelo roteado em {@code app.llm.routing}
 * não tiver preço cadastrado, evitando métrica de custo silenciosamente zerada.
 */
@Slf4j
@Component
public class LlmPricingRegistry {

    private static final String ARQUIVO_PRECOS = "llm-pricing.yml";

    private final LlmRoutingProperties routing;
    private final Map<String, PrecoModelo> precos;

    /**
     * Preços em USD por MTok. {@code cacheWritePerMtok} é {@code null} para
     * provedores sem tarifa explícita de cache write (ex.: OpenAI, que cacheia
     * por prefixo sem custo adicional de escrita).
     */
    public record PrecoModelo(
            BigDecimal inputPerMtok,
            BigDecimal cachedInputPerMtok,
            @Nullable BigDecimal cacheWritePerMtok,
            BigDecimal outputPerMtok) {
    }

    public LlmPricingRegistry(LlmRoutingProperties routing) {
        this.routing = routing;
        this.precos = carregarPrecos();
    }

    /**
     * Idempotent: YES — leitura de mapa imutável em memória.
     * Side Effects: NONE
     * Tenant-aware: NO — dado técnico global.
     *
     * @return preço do modelo, ou vazio se não cadastrado no yml
     */
    public Optional<PrecoModelo> precoDe(String modelId) {
        return Optional.ofNullable(precos.get(modelId));
    }

    /**
     * Valida que todo modelo roteado tem preço cadastrado — chamado no startup.
     *
     * Idempotent: YES
     * Side Effects: NONE (lança em caso de configuração inválida)
     * Tenant-aware: NO
     *
     * @throws IllegalStateException nomeando o primeiro conjunto de modelos sem preço
     */
    @PostConstruct
    public void validarRotasComPreco() {
        Set<String> semPreco = new LinkedHashSet<>();
        for (LlmRoutingProperties.RotaLlm rota : new LlmRoutingProperties.RotaLlm[]{
                routing.getSimple(), routing.getStandard(), routing.getComplex(),
                routing.getExpert(), routing.getPlano()}) {
            if (!precos.containsKey(rota.getModel())) {
                semPreco.add(rota.getModel());
            }
        }
        if (!semPreco.isEmpty()) {
            throw new IllegalStateException(
                    "Modelos roteados em app.llm.routing sem preço em " + ARQUIVO_PRECOS + ": " + semPreco);
        }
        log.info("[llm-pricing] {} modelos com preço carregados de {}", precos.size(), ARQUIVO_PRECOS);
    }

    private static Map<String, PrecoModelo> carregarPrecos() {
        try (InputStream in = new ClassPathResource(ARQUIVO_PRECOS).getInputStream()) {
            Map<String, Object> raiz = new Yaml().load(in);
            @SuppressWarnings("unchecked")
            Map<String, Map<String, Object>> modelos = (Map<String, Map<String, Object>>) raiz.get("modelos");
            if (modelos == null || modelos.isEmpty()) {
                throw new IllegalStateException("Bloco 'modelos' ausente ou vazio em " + ARQUIVO_PRECOS);
            }
            Map<String, PrecoModelo> resultado = new LinkedHashMap<>();
            modelos.forEach((modelId, campos) -> resultado.put(modelId, new PrecoModelo(
                    valorObrigatorio(modelId, campos, "input-per-mtok"),
                    valorObrigatorio(modelId, campos, "cached-input-per-mtok"),
                    valorOpcional(campos, "cache-write-per-mtok"),
                    valorObrigatorio(modelId, campos, "output-per-mtok"))));
            return Map.copyOf(resultado);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler " + ARQUIVO_PRECOS + " do classpath", e);
        }
    }

    private static BigDecimal valorObrigatorio(String modelId, Map<String, Object> campos, String chave) {
        Object valor = campos.get(chave);
        if (valor == null) {
            throw new IllegalStateException(
                    "Campo obrigatório '" + chave + "' ausente para o modelo '" + modelId + "' em " + ARQUIVO_PRECOS);
        }
        return new BigDecimal(String.valueOf(valor));
    }

    @Nullable
    private static BigDecimal valorOpcional(Map<String, Object> campos, String chave) {
        Object valor = campos.get(chave);
        return valor != null ? new BigDecimal(String.valueOf(valor)) : null;
    }
}
