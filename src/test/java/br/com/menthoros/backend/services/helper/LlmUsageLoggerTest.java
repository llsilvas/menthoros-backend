package br.com.menthoros.backend.services.helper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.api.OpenAiApi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("LlmUsageLogger")
class LlmUsageLoggerTest {

    @Nested
    @DisplayName("extrairCachedTokens")
    class ExtrairCachedTokens {

        @Test
        @DisplayName("lê cached_tokens do usage nativo da OpenAI")
        void leCachedTokensOpenAi() {
            var detalhes = new OpenAiApi.Usage.PromptTokensDetails(null, 3000);
            var nativeUsage = new OpenAiApi.Usage(null, null, null, detalhes, null);

            assertThat(LlmUsageLogger.extrairCachedTokens(nativeUsage)).isEqualTo(3000);
        }

        @Test
        @DisplayName("retorna null quando o usage OpenAI não tem promptTokensDetails")
        void semDetalhes() {
            var nativeUsage = new OpenAiApi.Usage(null, null, null, null, null);
            assertThat(LlmUsageLogger.extrairCachedTokens(nativeUsage)).isNull();
        }

        @Test
        @DisplayName("retorna null para native usage nulo ou de outro provedor")
        void outroProvedorOuNulo() {
            assertThat(LlmUsageLogger.extrairCachedTokens(null)).isNull();
            assertThat(LlmUsageLogger.extrairCachedTokens("não-é-openai")).isNull();
        }
    }

    @Nested
    @DisplayName("registrar")
    class Registrar {

        @Test
        @DisplayName("não lança para ChatResponse nulo (best-effort)")
        void naoLancaComNulo() {
            LlmUsageLogger logger = new LlmUsageLogger();
            assertThatCode(() -> logger.registrar(null)).doesNotThrowAnyException();
        }
    }
}
