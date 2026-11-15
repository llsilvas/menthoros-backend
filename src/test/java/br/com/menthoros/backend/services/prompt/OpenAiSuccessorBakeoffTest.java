package br.com.menthoros.backend.services.prompt;

import br.com.menthoros.backend.dto.eval.NotaJuizCompleta;
import br.com.menthoros.backend.services.helper.AthleteZones;
import br.com.menthoros.backend.services.helper.EvalDeterministicGrader;
import br.com.menthoros.backend.services.helper.EvalLlmJudge;
import br.com.menthoros.backend.services.prompt.PlanoPromptArquetipos.Arquetipo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.MockedStatic;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@Tag("eval")
@DisplayName("OpenAiSuccessorBakeoff")
class OpenAiSuccessorBakeoffTest {

    @Nested
    @DisplayName("relatório markdown")
    class Relatorio {

        @Test
        @DisplayName("tem o juiz no cabeçalho e uma linha por candidato com o modelo servido")
        void umaLinhaPorCandidato() {
            Arquetipo arq = PlanoPromptArquetipos.todos().get(0);
            var caso = new OpenAiSuccessorBakeoff.Caso(arq.nome(), prompt(arq), null,
                    new AthleteZones(190, 160, BigDecimal.valueOf(5.0)), arq.atleta(),
                    arq.inicioSemana(), "contexto");
            var candidatos = List.of(
                    new OpenAiSuccessorBakeoff.Candidato("gpt-4o", clienteQueResponde("gpt-4o-2024-08-06")),
                    new OpenAiSuccessorBakeoff.Candidato("gpt-6-luna:low", clienteQueResponde("gpt-6-luna-2026-10-01")),
                    new OpenAiSuccessorBakeoff.Candidato("gpt-6-luna:none", clienteQueFalha()));

            String md = OpenAiSuccessorBakeoff.rodar(candidatos, List.of(caso), juizComNota(4), "gpt-4o",
                    mock(EvalDeterministicGrader.class, Answers.RETURNS_DEEP_STUBS));

            assertThat(md).contains("Juiz: `gpt-4o`");
            assertThat(linhas(md)).hasSize(3);
            assertThat(linhas(md).get(0)).contains("gpt-4o-2024-08-06").contains("| 4.00 |");
            assertThat(linhas(md).get(1)).contains("gpt-6-luna-2026-10-01");
        }

        @Test
        @DisplayName("falha do candidato aparece na coluna de falhas e não derruba a rodada")
        void falhaNaoDerrubaARodada() {
            Arquetipo arq = PlanoPromptArquetipos.todos().get(0);
            var caso = new OpenAiSuccessorBakeoff.Caso(arq.nome(), prompt(arq), null,
                    new AthleteZones(190, 160, BigDecimal.valueOf(5.0)), arq.atleta(),
                    arq.inicioSemana(), "contexto");

            String md = OpenAiSuccessorBakeoff.rodar(
                    List.of(new OpenAiSuccessorBakeoff.Candidato("quebra", clienteQueFalha())),
                    List.of(caso), juizComNota(4), "gpt-4o",
                    mock(EvalDeterministicGrader.class, Answers.RETURNS_DEEP_STUBS));

            assertThat(linhas(md).get(0)).endsWith("| 1 |");
        }
    }

    private static List<String> linhas(String md) {
        return md.lines().filter(l -> l.startsWith("| ") && !l.startsWith("| candidato")).toList();
    }

    private static EvalLlmJudge juizComNota(int nota) {
        EvalLlmJudge juiz = mock(EvalLlmJudge.class);
        NotaJuizCompleta notaJuiz = mock(NotaJuizCompleta.class);
        when(notaJuiz.notaGeral()).thenReturn(nota);
        when(juiz.avaliarCompleta(anyString(), any())).thenReturn(new EvalLlmJudge.ResultadoCompleto(notaJuiz, null));
        return juiz;
    }

    private static ChatClient clienteQueResponde(String modeloServido) {
        ChatClient cliente = mock(ChatClient.class, Answers.RETURNS_DEEP_STUBS);
        CallResponseSpec chamada = mock(CallResponseSpec.class);
        when(cliente.prompt().system(anyString()).user(anyString())
                .options(org.mockito.ArgumentMatchers.<ChatOptions>any()).call()).thenReturn(chamada);
        when(chamada.chatResponse()).thenReturn(new ChatResponse(
                List.of(new Generation(new AssistantMessage("{}"))),
                ChatResponseMetadata.builder().usage(new DefaultUsage(10, 5)).model(modeloServido).build()));
        return cliente;
    }

    private static ChatClient clienteQueFalha() {
        ChatClient cliente = mock(ChatClient.class, Answers.RETURNS_DEEP_STUBS);
        when(cliente.prompt().system(anyString()).user(anyString())
                .options(org.mockito.ArgumentMatchers.<ChatOptions>any()).call())
                .thenThrow(new IllegalStateException("HTTP 400"));
        return cliente;
    }

    private static PlanoTreinoPromptBuilder.PromptGerado prompt(Arquetipo arq) {
        var provider = mock(br.com.menthoros.backend.services.helper.TreinoHistoricoProvider.class);
        when(provider.prepararContexto(any())).thenReturn(arq.contexto());
        var builder = PlanoPromptArquetipos.builder(provider);
        try (MockedStatic<LocalDate> now = mockStatic(LocalDate.class, CALLS_REAL_METHODS)) {
            now.when(LocalDate::now).thenReturn(PlanoPromptArquetipos.HOJE);
            return builder.buildOptimizedPrompt(arq.atleta(), arq.meta(), arq.prova(), arq.inicioSemana(),
                    arq.diasEfetivos());
        }
    }
}
