package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.ai.ledger.Violacao;
import br.com.menthoros.backend.exception.LLMException;
import br.com.menthoros.backend.services.helper.LlmUsageLogger;
import br.com.menthoros.backend.services.helper.PlanoResilienceService;
import br.com.menthoros.backend.services.helper.RepairTurnMessageBuilder;
import br.com.menthoros.backend.services.prompt.LlmJsonSchemaBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;

/**
 * {@code plan-generation-repair-turn}, seção 4 — testa {@code IaServiceImpl#gerarChamadaLlm} por
 * reflexão (método privado; o fluxo completo de {@code geraPlanoSemanalAvancado} exige fixtures
 * inviáveis em unit test, mesma razão de {@link IaServiceImplComplianceEstagio1Test}).
 *
 * <p>Usa o {@link ChatClient} <b>real</b> ({@code ChatClient.builder(chatModel).build()} — cadeia
 * de advisors default incluída) com só o {@code ChatModel} mockado — mesma costura de
 * {@code ChatClientRepairTurnPrefixSpikeTest} (task 0.1). Isso prova mais do que a task 4.2 pedia
 * (mockar só o {@code ChatClient} bypassaria a cadeia de advisors): a igualdade de prefixo entre
 * tentativas é verificada aqui contra o request real que o {@code ChatModel} recebe.</p>
 */
@DisplayName("IaServiceImpl — gerarChamadaLlm (turno de reparo)")
class IaServiceImplGerarChamadaLlmTest {

    private IaServiceImpl service;
    private OpenAiChatModel chatModel;
    private ChatClient chatClient;
    private LlmUsageLogger llmUsageLogger;

    @BeforeEach
    void setUp() {
        chatModel = mock(OpenAiChatModel.class);
        chatClient = ChatClient.builder(chatModel).build();
        llmUsageLogger = new LlmUsageLogger();

        service = new IaServiceImpl(
                mock(br.com.menthoros.backend.routing.ModelRouter.class),
                mock(br.com.menthoros.backend.services.prompt.PlanoTreinoPromptBuilder.class),
                new LlmJsonSchemaBuilder(),
                mock(br.com.menthoros.backend.repository.AtletaRepository.class),
                mock(br.com.menthoros.backend.services.helper.RegraGeracaoTreino.class),
                mock(br.com.menthoros.backend.services.quality.PlanQualityChecker.class),
                mock(br.com.menthoros.backend.services.helper.PlanoLlmValidator.class),
                mock(PlanoResilienceService.class),
                new SimpleMeterRegistry(),
                llmUsageLogger,
                mock(br.com.menthoros.backend.services.helper.PlannerShadowService.class),
                mock(br.com.menthoros.backend.services.helper.PlanoLlmLedgerHook.class),
                new RepairTurnMessageBuilder(),
                new ObjectMapper(),
                mock(br.com.menthoros.backend.services.helper.SchemaVersionResolver.class),
                mock(br.com.menthoros.backend.services.helper.SessionResolver.class));
    }

    private void chatModelResponde(String texto) {
        ChatResponse resposta = new ChatResponse(
                List.of(new Generation(new AssistantMessage(texto))),
                ChatResponseMetadata.builder().usage(new DefaultUsage(10, 5)).model("gpt-4o").build());
        when(chatModel.call(any(Prompt.class))).thenReturn(resposta);
    }

    private void chatModelRespondeVazio() {
        ChatResponse resposta = new ChatResponse(List.of(),
                ChatResponseMetadata.builder().usage(new DefaultUsage(10, 0)).model("gpt-4o").build());
        when(chatModel.call(any(Prompt.class))).thenReturn(resposta);
    }

    private Object invoke(String system, PlanoResilienceService.Tentativa tentativa) throws Exception {
        Method m = IaServiceImpl.class.getDeclaredMethod("gerarChamadaLlm",
                ChatClient.class, String.class, PlanoResilienceService.Tentativa.class);
        m.setAccessible(true);
        try {
            return m.invoke(service, chatClient, system, tentativa);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException re) throw re;
            throw e;
        }
    }

    @Nested
    @DisplayName("1ª tentativa")
    class PrimeiraTentativa {

        @Test
        @DisplayName("envia system + user simples; parseia a entidade e captura o jsonBruto")
        void systemMaisUserSimples() throws Exception {
            chatModelResponde("{\"volumePlanejadoKm\":30.0,\"volumeAlvoKm\":30.0,\"status\":\"ATIVO\","
                    + "\"objetivoSemanal\":\"base\",\"treinosPlanejados\":[]}");

            var chamada = (PlanoResilienceService.ChamadaLlm) invoke("system fixo",
                    new PlanoResilienceService.Tentativa(1, "gere o plano", null, List.of()));

            assertThat(chamada.entidade()).isNotNull();
            assertThat(chamada.entidade().volumePlanejadoKm()).isEqualTo(30.0);
            assertThat(chamada.jsonBruto()).contains("volumePlanejadoKm");

            ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
            verify(chatModel, times(1)).call(captor.capture());
            List<Message> mensagens = captor.getValue().getInstructions();
            assertThat(mensagens).hasSize(2);
            assertThat(mensagens.get(0)).isInstanceOf(SystemMessage.class);
            assertThat(mensagens.get(1)).isInstanceOf(UserMessage.class);
            assertThat(mensagens.get(1).getText()).isEqualTo("gere o plano");
        }

        @Test
        @DisplayName("content() nulo (resposta sem choices) → entidade nula, sem lançar (guarda de retry)")
        void contentNuloEntidadeNula() throws Exception {
            chatModelRespondeVazio();

            var chamada = (PlanoResilienceService.ChamadaLlm) invoke("system fixo",
                    new PlanoResilienceService.Tentativa(1, "gere o plano", null, List.of()));

            assertThat(chamada.entidade()).isNull();
            assertThat(chamada.jsonBruto()).isNull();
        }

        @Test
        @DisplayName("JSON não-vazio malformado → lança LLMException (sem retry, dentro de gerar)")
        void jsonMalformadoLanca() {
            chatModelResponde("isto não é um json válido {{{");

            assertThatThrownBy(() -> invoke("system fixo",
                    new PlanoResilienceService.Tentativa(1, "gere o plano", null, List.of())))
                    .isInstanceOf(LLMException.class)
                    .hasMessageContaining("JSON válido");
        }
    }

    @Nested
    @DisplayName("turno de reparo (2ª tentativa)")
    class TurnoDeReparo {

        @Test
        @DisplayName("acrescenta assistant(jsonAnterior) + user(correção); system/user originais idênticos à 1ª")
        void mensagensAcrescentadas() throws Exception {
            chatModelResponde("{\"volumePlanejadoKm\":30.0,\"volumeAlvoKm\":30.0,\"status\":\"ATIVO\","
                    + "\"objetivoSemanal\":\"base\",\"treinosPlanejados\":[]}");

            // 1ª tentativa
            invoke("system fixo", new PlanoResilienceService.Tentativa(1, "gere o plano", null, List.of()));

            // 2ª tentativa — turno de reparo, com histórico da 1ª
            var violacoes = List.of(new Violacao("NORMALIZACAO_SEGUNDA", "Treino SEGUNDA inválido"));
            invoke("system fixo", new PlanoResilienceService.Tentativa(
                    2, "gere o plano", "{\"json\":\"anterior\"}", violacoes));

            ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
            verify(chatModel, times(2)).call(captor.capture());
            List<Message> m1 = captor.getAllValues().get(0).getInstructions();
            List<Message> m2 = captor.getAllValues().get(1).getInstructions();

            assertThat(m2).hasSize(4);
            assertThat(m2.get(0).getText()).isEqualTo(m1.get(0).getText()); // system idêntico
            assertThat(m2.get(1).getText()).isEqualTo(m1.get(1).getText()); // user original idêntico
            assertThat(m2.get(2)).isInstanceOf(AssistantMessage.class);
            assertThat(m2.get(2).getText()).isEqualTo("{\"json\":\"anterior\"}");
            assertThat(m2.get(3)).isInstanceOf(UserMessage.class);
            assertThat(m2.get(3).getText()).contains("NORMALIZACAO_SEGUNDA").contains("Treino SEGUNDA inválido");
        }

        @Test
        @DisplayName("achado do /qa: jsonAnterior nulo (1ª resposta vazia) não envia AssistantMessage(null) — usa fallback")
        void jsonAnteriorNuloUsaFallback() throws Exception {
            chatModelResponde("{\"volumePlanejadoKm\":30.0,\"volumeAlvoKm\":30.0,\"status\":\"ATIVO\","
                    + "\"objetivoSemanal\":\"base\",\"treinosPlanejados\":[]}");

            var violacoes = List.of(new Violacao("LLM_ERRO_ESTRUTURAL", "Plano gerado está nulo ou sem treinos"));
            invoke("system fixo", new PlanoResilienceService.Tentativa(2, "gere o plano", null, violacoes));

            ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
            verify(chatModel, times(1)).call(captor.capture());
            List<Message> mensagens = captor.getValue().getInstructions();

            assertThat(mensagens.get(2)).isInstanceOf(AssistantMessage.class);
            assertThat(mensagens.get(2).getText()).isNotNull().isNotBlank();
        }

        @Test
        @DisplayName("achado do /qa (2ª verificação): jsonAnterior em branco (não-nulo, só espaços) também usa fallback")
        void jsonAnteriorEmBrancoUsaFallback() throws Exception {
            chatModelResponde("{\"volumePlanejadoKm\":30.0,\"volumeAlvoKm\":30.0,\"status\":\"ATIVO\","
                    + "\"objetivoSemanal\":\"base\",\"treinosPlanejados\":[]}");

            var violacoes = List.of(new Violacao("LLM_ERRO_ESTRUTURAL", "Plano gerado está nulo ou sem treinos"));
            invoke("system fixo", new PlanoResilienceService.Tentativa(2, "gere o plano", "   ", violacoes));

            ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
            verify(chatModel, times(1)).call(captor.capture());
            List<Message> mensagens = captor.getValue().getInstructions();

            assertThat(mensagens.get(2)).isInstanceOf(AssistantMessage.class);
            assertThat(mensagens.get(2).getText()).isNotBlank().isNotEqualTo("   ")
                    .contains("resposta vazia");
        }
    }
}
