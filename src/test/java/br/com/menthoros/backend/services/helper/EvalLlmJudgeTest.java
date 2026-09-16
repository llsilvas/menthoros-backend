package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.services.prompt.EvalJudgeSchemaBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EvalLlmJudge")
class EvalLlmJudgeTest {

    private final EvalJudgeSchemaBuilder schemaBuilder = new EvalJudgeSchemaBuilder();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Nested
    @DisplayName("avaliarReduzida")
    class AvaliarReduzida {

        @Test
        @DisplayName("faz parse da resposta em NotaJuizReduzida")
        void fazParseDaResposta() {
            String json = """
                    {"polarizacao": {"nota": 4, "justificativa": "boa mistura"},
                     "especificidadeParaProva": {"nota": 3, "justificativa": "ok"},
                     "clareza": {"nota": 5, "justificativa": "muito clara"},
                     "notaGeral": 4, "justificativaGeral": "bom plano"}""";
            EvalLlmJudge judge = judgeComRespostaFixa(json);

            var resultado = judge.avaliarReduzida("{\"treinosPlanejados\":[]}");

            assertThat(resultado.nota().polarizacao().nota()).isEqualTo(4);
            assertThat(resultado.nota().notaGeral()).isEqualTo(4);
        }

        @Test
        @DisplayName("resposta vazia lança IllegalStateException")
        void respostaVaziaLanca() {
            EvalLlmJudge judge = judgeComRespostaFixa("");

            assertThat(catchThrowable(() -> judge.avaliarReduzida("{}")))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("JSON inválido lança IllegalStateException, não deixa passar em silêncio")
        void jsonInvalidoLanca() {
            EvalLlmJudge judge = judgeComRespostaFixa("{ não é json");

            assertThat(catchThrowable(() -> judge.avaliarReduzida("{}")))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("avaliarCompleta")
    class AvaliarCompleta {

        @Test
        @DisplayName("faz parse da resposta em NotaJuizCompleta, com os 6 eixos")
        void fazParseDaResposta() {
            String json = """
                    {"progressao": {"nota": 3, "justificativa": "ok"},
                     "polarizacao": {"nota": 4, "justificativa": "boa mistura"},
                     "especificidadeParaProva": {"nota": 3, "justificativa": "ok"},
                     "clareza": {"nota": 5, "justificativa": "muito clara"},
                     "exequibilidadeCarga": {"nota": 4, "justificativa": "cabe na rotina"},
                     "segurancaLesao": {"nota": 5, "justificativa": "respeita a lesão"},
                     "notaGeral": 4, "justificativaGeral": "bom plano"}""";
            EvalLlmJudge judge = judgeComRespostaFixa(json);

            var resultado = judge.avaliarCompleta("{\"treinosPlanejados\":[]}", "atleta com lesão ativa");

            assertThat(resultado.nota().progressao().nota()).isEqualTo(3);
            assertThat(resultado.nota().segurancaLesao().nota()).isEqualTo(5);
        }
    }

    private EvalLlmJudge judgeComRespostaFixa(String resposta) {
        ChatClient chatClient = mock(ChatClient.class, Answers.RETURNS_DEEP_STUBS);
        CallResponseSpec callResponse = mock(CallResponseSpec.class);
        when(chatClient.prompt().system(anyString()).user(anyString())
                .options(any(org.springframework.ai.chat.prompt.ChatOptions.class)).call())
                .thenReturn(callResponse);
        ChatResponse chatResponse = resposta.isEmpty() ? null : new ChatResponse(
                List.of(new Generation(new AssistantMessage(resposta))),
                ChatResponseMetadata.builder().usage(new DefaultUsage(10, 5)).model("gpt-4o").build());
        when(callResponse.chatResponse()).thenReturn(chatResponse);
        return new EvalLlmJudge(chatClient, schemaBuilder, objectMapper);
    }
}
