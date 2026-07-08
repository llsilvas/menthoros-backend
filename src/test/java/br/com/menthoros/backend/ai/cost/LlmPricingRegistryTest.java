package br.com.menthoros.backend.ai.cost;

import static org.assertj.core.api.Assertions.*;

import br.com.menthoros.backend.config.external.LlmRoutingProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

class LlmPricingRegistryTest {

    private static LlmRoutingProperties.RotaLlm rota(String model) {
        LlmRoutingProperties.RotaLlm rota = new LlmRoutingProperties.RotaLlm();
        rota.setModel(model);
        rota.setTemperature(0.2);
        rota.setMaxTokens(1000);
        return rota;
    }

    private static LlmRoutingProperties routingVigente() {
        LlmRoutingProperties props = new LlmRoutingProperties();
        props.setSimple(rota("gpt-4o-mini"));
        props.setStandard(rota("claude-haiku-4-5-20251001"));
        props.setComplex(rota("claude-sonnet-4-6"));
        props.setExpert(rota("gpt-4o"));
        props.setPlano(rota("gpt-4o"));
        return props;
    }

    @Nested
    @DisplayName("precoDe")
    class PrecoDe {

        @Test
        @DisplayName("retorna preços do llm-pricing.yml para um modelo cadastrado")
        void retornaPrecosDoYml() {
            LlmPricingRegistry registry = new LlmPricingRegistry(routingVigente());

            LlmPricingRegistry.PrecoModelo preco = registry.precoDe("gpt-4o").orElseThrow();

            assertThat(preco.inputPerMtok()).isEqualByComparingTo(new BigDecimal("2.50"));
            assertThat(preco.outputPerMtok()).isEqualByComparingTo(new BigDecimal("10.00"));
            assertThat(preco.cachedInputPerMtok()).isEqualByComparingTo(new BigDecimal("1.25"));
        }

        @Test
        @DisplayName("retorna preço de cache write para modelos Anthropic (TTL 1h = 2x input)")
        void retornaCacheWriteAnthropic() {
            LlmPricingRegistry registry = new LlmPricingRegistry(routingVigente());

            LlmPricingRegistry.PrecoModelo preco = registry.precoDe("claude-sonnet-4-6").orElseThrow();

            assertThat(preco.inputPerMtok()).isEqualByComparingTo(new BigDecimal("3.00"));
            assertThat(preco.cacheWritePerMtok()).isEqualByComparingTo(new BigDecimal("6.00"));
            assertThat(preco.cachedInputPerMtok()).isEqualByComparingTo(new BigDecimal("0.30"));
            assertThat(preco.outputPerMtok()).isEqualByComparingTo(new BigDecimal("15.00"));
        }

        @Test
        @DisplayName("retorna Optional vazio para modelo sem preço cadastrado")
        void vazioParaModeloDesconhecido() {
            LlmPricingRegistry registry = new LlmPricingRegistry(routingVigente());

            assertThat(registry.precoDe("modelo-inexistente")).isEmpty();
        }
    }

    @Nested
    @DisplayName("validarRotasComPreco")
    class ValidarRotasComPreco {

        @Test
        @DisplayName("passa quando todas as rotas configuradas têm preço no yml")
        void passaComRotasVigentes() {
            LlmPricingRegistry registry = new LlmPricingRegistry(routingVigente());

            assertThatCode(registry::validarRotasComPreco).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("falha rápido nomeando o modelo roteado sem preço")
        void falhaComModeloSemPreco() {
            LlmRoutingProperties props = routingVigente();
            props.setExpert(rota("gpt-9-experimental"));
            LlmPricingRegistry registry = new LlmPricingRegistry(props);

            assertThatThrownBy(registry::validarRotasComPreco)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("gpt-9-experimental")
                    .hasMessageContaining("llm-pricing.yml");
        }
    }
}
