package br.com.menthoros.backend.services.prompt;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.services.helper.AthleteZones;
import br.com.menthoros.backend.services.helper.EvalCandidateRunner;
import br.com.menthoros.backend.services.helper.EvalDeterministicGrader;
import br.com.menthoros.backend.services.helper.PlannerShadowService;
import br.com.menthoros.backend.services.helper.SessionResolver;
import br.com.menthoros.backend.services.helper.TreinoHistoricoProvider;
import br.com.menthoros.backend.services.helper.ZoneResolver;
import br.com.menthoros.backend.services.helper.ZonaTreinoService;
import br.com.menthoros.backend.services.prompt.PlanoPromptArquetipos.Arquetipo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
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
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Modo candidato (plan-generation-eval-set, task 1.7) — prova CA6: mudar o prompt/schema muda a
 * nota, porque o modo candidato de fato reexecuta a LLM em vez de reavaliar uma resposta
 * congelada (achado do Codex, rodada 1 de DoR). Aqui o "LLM" é mockado — a validação com a LLM
 * real acontece manualmente (fora do CI), ver proposal.md.
 */
@Tag("eval")
@DisplayName("Modo candidato (EvalCandidateRunner)")
class EvalCandidateModeTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    @DisplayName("CA6 — respostas diferentes do LLM produzem notas diferentes para a mesma fixture")
    void respostasDiferentesProduzemNotasDiferentes() throws Exception {
        Arquetipo arq = PlanoPromptArquetipos.todos().get(0);
        PlanoTreinoPromptBuilder.PromptGerado prompt = montarPrompt(arq);

        String respostaConforme = respostaComTreino(arq, "SEGUNDA");
        String respostaComViolacao = respostaComTreino(arq, "DOMINGO"); // fora de diasUteis() — sob DIAS_PERMITIDOS

        EvalCandidateRunner runnerConforme = runnerComRespostaFixa(respostaConforme);
        EvalCandidateRunner runnerComViolacao = runnerComRespostaFixa(respostaComViolacao);

        var zonas = new AthleteZones(190, 160, BigDecimal.valueOf(5.0));
        var resultadoConforme = runnerConforme.rodar(prompt, null, zonas, arq.atleta(), arq.inicioSemana());
        var resultadoComViolacao = runnerComViolacao.rodar(prompt, null, zonas, arq.atleta(), arq.inicioSemana());

        assertThat(resultadoConforme.avaliacao().violacoesQualidade()).isEmpty();
        assertThat(resultadoComViolacao.avaliacao().violacoesQualidade()).isNotEmpty();
    }

    @Test
    @DisplayName("resposta vazia da LLM lança, não deixa passar como avaliação vazia")
    void respostaVaziaLanca() throws Exception {
        Arquetipo arq = PlanoPromptArquetipos.todos().get(0);
        PlanoTreinoPromptBuilder.PromptGerado prompt = montarPrompt(arq);
        EvalCandidateRunner runner = runnerComRespostaFixa("");
        var zonas = new AthleteZones(190, 160, BigDecimal.valueOf(5.0));

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
                runner.rodar(prompt, null, zonas, arq.atleta(), arq.inicioSemana())))
                .isInstanceOf(IllegalStateException.class);
    }

    private PlanoTreinoPromptBuilder.PromptGerado montarPrompt(Arquetipo arq) {
        TreinoHistoricoProvider provider = mock(TreinoHistoricoProvider.class);
        when(provider.prepararContexto(any())).thenReturn(arq.contexto());
        PlanoTreinoPromptBuilder builder = PlanoPromptArquetipos.builder(provider);

        try (MockedStatic<LocalDate> now = mockStatic(LocalDate.class, CALLS_REAL_METHODS)) {
            now.when(LocalDate::now).thenReturn(PlanoPromptArquetipos.HOJE);
            return builder.buildOptimizedPrompt(
                    arq.atleta(), arq.meta(), arq.prova(), arq.inicioSemana(), arq.diasEfetivos());
        }
    }

    private String respostaComTreino(Arquetipo arq, String diaSemana) throws Exception {
        var etapa = new br.com.menthoros.backend.dto.llm.EtapaTreinoLlmDto(1, "AQUECIMENTO",
                "aquecimento", 10, 1.0, "107-121 bpm", 1, "9:00/km");
        var treino = new br.com.menthoros.backend.dto.llm.TreinoPlanejadoLlmDto(diaSemana, "CONTINUO",
                "121-133 bpm", 40, 0.8, 5, "justificativa", "44:00", 7.0, "8:30/km", java.util.List.of(etapa));
        var plano = new br.com.menthoros.backend.dto.llm.PlanoSemanalLlmDto(20.0, 20.0, 5.0, 5.0,
                "PLANEJADO", "objetivo", java.util.List.of(treino));
        return OBJECT_MAPPER.writeValueAsString(plano);
    }

    private EvalCandidateRunner runnerComRespostaFixa(String resposta) {
        ChatClient chatClient = mock(ChatClient.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
        CallResponseSpec callResponse = mock(CallResponseSpec.class);
        when(chatClient.prompt().system(anyString()).user(anyString())
                .options(org.mockito.ArgumentMatchers.<ChatOptions>any()).call())
                .thenReturn(callResponse);
        ChatResponse chatResponse = resposta.isEmpty() ? null : new ChatResponse(
                List.of(new Generation(new AssistantMessage(resposta))),
                ChatResponseMetadata.builder().usage(new DefaultUsage(10, 5)).model("gpt-4o").build());
        when(callResponse.chatResponse()).thenReturn(chatResponse);

        var llmJsonSchemaBuilder = new LlmJsonSchemaBuilder();
        var sessionResolver = new SessionResolver(new ZoneResolver(new ZonaTreinoService()),
                new br.com.menthoros.backend.services.helper.TssCalculatorService());
        var plannerShadowService = mock(PlannerShadowService.class);
        var grader = new EvalDeterministicGrader(OBJECT_MAPPER, sessionResolver, plannerShadowService);
        return new EvalCandidateRunner(chatClient, llmJsonSchemaBuilder, grader);
    }

    private static String anyString() {
        return org.mockito.ArgumentMatchers.anyString();
    }
}
