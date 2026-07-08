package br.com.menthoros.backend.config.external;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import br.com.menthoros.backend.ai.cost.LlmPricingRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.util.List;

class MultiModelConfigTest {

    private static LlmRoutingProperties.RotaLlm rota(String model, double temperature, int maxTokens) {
        LlmRoutingProperties.RotaLlm rota = new LlmRoutingProperties.RotaLlm();
        rota.setModel(model);
        rota.setTemperature(temperature);
        rota.setMaxTokens(maxTokens);
        return rota;
    }

    private static LlmRoutingProperties routingVigente() {
        LlmRoutingProperties props = new LlmRoutingProperties();
        props.setSimple(rota("gpt-4o-mini", 0.3, 1000));
        props.setStandard(rota("claude-haiku-4-5-20251001", 0.5, 2000));
        props.setComplex(rota("claude-sonnet-4-6", 0.7, 4000));
        props.setExpert(rota("gpt-4o", 0.2, 8000));
        props.setPlano(rota("gpt-4o", 0.2, 12000));
        return props;
    }

    @Nested
    @DisplayName("opcoesOpenAi")
    class OpcoesOpenAi {

        @Test
        @DisplayName("constrói options com model, temperature e maxTokens da rota")
        void constroiAPartirDaRota() {
            OpenAiChatOptions options = MultiModelConfig.opcoesOpenAi(rota("gpt-4.1", 0.2, 8000));

            assertThat(options.getModel()).isEqualTo("gpt-4.1");
            assertThat(options.getTemperature()).isEqualTo(0.2);
            assertThat(options.getMaxTokens()).isEqualTo(8000);
        }
    }

    @Nested
    @DisplayName("opcoesAnthropic")
    class OpcoesAnthropic {

        @Test
        @DisplayName("constrói options com model, temperature, maxTokens e cache SYSTEM_ONLY de 1h")
        void constroiAPartirDaRotaComCache() {
            AnthropicChatOptions options = MultiModelConfig.opcoesAnthropic(rota("claude-sonnet-4-6", 0.7, 4000));

            assertThat(options.getModel()).isEqualTo("claude-sonnet-4-6");
            assertThat(options.getTemperature()).isEqualTo(0.7);
            assertThat(options.getMaxTokens()).isEqualTo(4000);
            assertThat(options.getCacheOptions()).isNotNull();
        }
    }

    @Nested
    @DisplayName("costTrackingAdvisor")
    class CostTracking {

        @Test
        @DisplayName("chamada via bean incrementa métricas de custo com a tag da rota")
        void beanRegistraMetricasComRota() {
            LlmRoutingProperties props = routingVigente();
            SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
            MultiModelConfig config = new MultiModelConfig(
                    props, new LlmPricingRegistry(props), meterRegistry);

            OpenAiChatModel chatModel = mock(OpenAiChatModel.class);
            ChatResponse resposta = new ChatResponse(
                    List.of(new Generation(new AssistantMessage("ok"))),
                    ChatResponseMetadata.builder()
                            .usage(new DefaultUsage(100, 50))
                            .model("gpt-4o-mini")
                            .build());
            when(chatModel.call(any(Prompt.class))).thenReturn(resposta);

            config.gpt4oMiniClient(chatModel).prompt().user("olá").call().content();

            var counter = meterRegistry.find("llm.tokens.input")
                    .tags("model", "gpt-4o-mini", "route", "simple").counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(100.0);
        }
    }
}
