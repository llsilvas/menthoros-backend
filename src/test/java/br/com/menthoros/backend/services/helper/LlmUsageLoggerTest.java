package br.com.menthoros.backend.services.helper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.api.OpenAiApi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

        private final LlmUsageLogger logger = new LlmUsageLogger();

        @Test
        @DisplayName("não lança para ChatResponse nulo (best-effort)")
        void naoLancaComNulo() {
            assertThatCode(() -> logger.registrar(null)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("não lança quando o metadata é nulo")
        void metadataNulo() {
            ChatResponse response = mock(ChatResponse.class);
            when(response.getMetadata()).thenReturn(null);
            assertThatCode(() -> logger.registrar(response)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("não lança quando o usage é nulo")
        void usageNulo() {
            ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
            when(metadata.getUsage()).thenReturn(null);
            ChatResponse response = mock(ChatResponse.class);
            when(response.getMetadata()).thenReturn(metadata);
            assertThatCode(() -> logger.registrar(response)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("caminho feliz: usage válido com cached_tokens → registra sem lançar")
        void usageValido() {
            var detalhes = new OpenAiApi.Usage.PromptTokensDetails(null, 3000);
            var nativeUsage = new OpenAiApi.Usage(null, null, null, detalhes, null);
            Usage usage = mock(Usage.class);
            when(usage.getPromptTokens()).thenReturn(5000);
            when(usage.getCompletionTokens()).thenReturn(800);
            when(usage.getNativeUsage()).thenReturn(nativeUsage);

            ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
            when(metadata.getUsage()).thenReturn(usage);
            ChatResponse response = mock(ChatResponse.class);
            when(response.getMetadata()).thenReturn(metadata);

            assertThatCode(() -> logger.registrar(response)).doesNotThrowAnyException();
        }
    }
}
