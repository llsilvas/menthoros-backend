package br.com.menthoros.backend.config.external;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;

class MultiModelConfigTest {

    private static LlmRoutingProperties.RotaLlm rota(String model, double temperature, int maxTokens) {
        LlmRoutingProperties.RotaLlm rota = new LlmRoutingProperties.RotaLlm();
        rota.setModel(model);
        rota.setTemperature(temperature);
        rota.setMaxTokens(maxTokens);
        return rota;
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
}
