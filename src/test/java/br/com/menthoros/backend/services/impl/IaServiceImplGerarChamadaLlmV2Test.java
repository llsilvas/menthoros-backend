package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.exception.LLMException;
import br.com.menthoros.backend.services.helper.LlmUsageLogger;
import br.com.menthoros.backend.services.helper.PlanoResilienceService;
import br.com.menthoros.backend.services.helper.RepairTurnMessageBuilder;
import br.com.menthoros.backend.services.helper.SessionResolver;
import br.com.menthoros.backend.services.helper.ZoneResolver;
import br.com.menthoros.backend.services.helper.ZonaTreinoService;
import br.com.menthoros.backend.services.helper.TssCalculatorService;
import br.com.menthoros.backend.services.prompt.LlmJsonSchemaBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import org.mockito.ArgumentCaptor;

/**
 * {@code semantic-session-schema}, task 9.2 — testa {@code IaServiceImpl#gerarChamadaLlmV2} por
 * reflexão, mesmo padrão de {@link IaServiceImplGerarChamadaLlmTest} (v1). Usa
 * {@link SessionResolver} real (compõe {@link ZoneResolver}/{@link TssCalculatorService} reais) —
 * prova a resolução de blocos→etapas ponta a ponta, não só o wiring da chamada.
 */
@DisplayName("IaServiceImpl — gerarChamadaLlmV2 (semantic-session-schema)")
class IaServiceImplGerarChamadaLlmV2Test {

    private IaServiceImpl service;
    private OpenAiChatModel chatModel;
    private ChatClient chatClient;
    private Atleta atleta;

    @BeforeEach
    void setUp() {
        chatModel = mock(OpenAiChatModel.class);
        chatClient = ChatClient.builder(chatModel).build();
        atleta = Atleta.builder()
                .id(UUID.randomUUID())
                .fcMaxima(190)
                .fcLimiar(160)
                .paceLimiar(BigDecimal.valueOf(4.50))
                .build();

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
                new LlmUsageLogger(),
                mock(br.com.menthoros.backend.services.helper.PlannerShadowService.class),
                mock(br.com.menthoros.backend.services.helper.PlanoLlmLedgerHook.class),
                new RepairTurnMessageBuilder(),
                new ObjectMapper(),
                mock(br.com.menthoros.backend.services.helper.SchemaVersionResolver.class),
                new SessionResolver(new ZoneResolver(new ZonaTreinoService()), new TssCalculatorService()));
    }

    private static final String JSON_V2_VALIDO =
            "{\"volumePlanejadoKm\":10.0,\"volumeAlvoKm\":10.0,\"status\":\"ATIVO\",\"objetivoSemanal\":\"base\","
            + "\"treinosPlanejados\":[{\"diaSemana\":\"SEGUNDA\",\"tipoTreino\":\"REGENERATIVO\","
            + "\"justificativaIa\":\"recuperação\",\"blocos\":["
            + "{\"papel\":\"AQUEC\",\"repeticoes\":1,\"quantidadePorRepeticao\":5,\"unidade\":\"MIN\",\"zona\":\"Z1\"},"
            + "{\"papel\":\"PRINCIPAL\",\"repeticoes\":1,\"quantidadePorRepeticao\":20,\"unidade\":\"MIN\",\"zona\":\"Z1\"},"
            + "{\"papel\":\"DESAQ\",\"repeticoes\":1,\"quantidadePorRepeticao\":5,\"unidade\":\"MIN\",\"zona\":\"Z1\"}"
            + "]}]}";

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
        Method m = IaServiceImpl.class.getDeclaredMethod("gerarChamadaLlmV2",
                ChatClient.class, String.class, PlanoResilienceService.Tentativa.class, Atleta.class);
        m.setAccessible(true);
        try {
            return m.invoke(service, chatClient, system, tentativa, atleta);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException re) throw re;
            throw e;
        }
    }

    @Nested
    @DisplayName("1ª tentativa")
    class PrimeiraTentativa {

        @Test
        @DisplayName("resolve o JSON v2 (blocos) para o shape v1 (etapas absolutas) via SessionResolver real")
        void resolveParaShapeV1() throws Exception {
            chatModelResponde(JSON_V2_VALIDO);

            var chamada = (PlanoResilienceService.ChamadaLlm) invoke("system v2",
                    new PlanoResilienceService.Tentativa(1, "gere o plano", null, List.of()));

            assertThat(chamada.entidade()).isNotNull();
            assertThat(chamada.entidade().treinosPlanejados()).hasSize(1);
            var treino = chamada.entidade().treinosPlanejados().get(0);
            assertThat(treino.etapas()).hasSize(3); // AQUEC, PRINCIPAL, DESAQ — resolvidos, não blocos
            assertThat(treino.duracaoMin()).isEqualTo("30:00"); // 5+20+5 min
            assertThat(treino.tssPlanejado()).isNotNull().isPositive();
            assertThat(treino.fcAlvo()).matches("\\d+-\\d+ bpm");
            assertThat(chamada.jsonBruto()).isEqualTo(JSON_V2_VALIDO); // jsonBruto é o texto v2 cru, não o resolvido
        }

        @Test
        @DisplayName("usa v2JsonSchemaOptions() — schema diferente do v1")
        void usaSchemaV2() throws Exception {
            chatModelResponde(JSON_V2_VALIDO);

            invoke("system v2", new PlanoResilienceService.Tentativa(1, "gere o plano", null, List.of()));

            ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
            org.mockito.Mockito.verify(chatModel).call(captor.capture());
            var schemaEnviado = captor.getValue().getOptions();
            assertThat(schemaEnviado).isNotNull();
        }

        @Test
        @DisplayName("content() nulo (resposta sem choices) → entidade nula, sem lançar (guarda de retry)")
        void contentNuloEntidadeNula() throws Exception {
            chatModelRespondeVazio();

            var chamada = (PlanoResilienceService.ChamadaLlm) invoke("system v2",
                    new PlanoResilienceService.Tentativa(1, "gere o plano", null, List.of()));

            assertThat(chamada.entidade()).isNull();
            assertThat(chamada.jsonBruto()).isNull();
        }

        @Test
        @DisplayName("JSON v2 malformado → lança LLMException (sem retry, dentro de gerar)")
        void jsonMalformadoLanca() {
            chatModelResponde("isto não é um json válido {{{");

            assertThatThrownBy(() -> invoke("system v2",
                    new PlanoResilienceService.Tentativa(1, "gere o plano", null, List.of())))
                    .isInstanceOf(LLMException.class)
                    .hasMessageContaining("JSON válido");
        }
    }

    @Nested
    @DisplayName("turno de reparo (2ª tentativa)")
    class TurnoDeReparo {

        @Test
        @DisplayName("acrescenta assistant(jsonAnterior v2) + user(correção); system/user originais idênticos à 1ª")
        void mensagensAcrescentadas() throws Exception {
            chatModelResponde(JSON_V2_VALIDO);

            invoke("system v2", new PlanoResilienceService.Tentativa(1, "gere o plano", null, List.of()));

            var violacoes = List.of(new br.com.menthoros.backend.ai.ledger.Violacao(
                    "NORMALIZACAO_V2_SEGUNDA", "Treino SEGUNDA inválido"));
            invoke("system v2", new PlanoResilienceService.Tentativa(2, "gere o plano", JSON_V2_VALIDO, violacoes));

            ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
            org.mockito.Mockito.verify(chatModel, org.mockito.Mockito.times(2)).call(captor.capture());
            List<Message> m1 = captor.getAllValues().get(0).getInstructions();
            List<Message> m2 = captor.getAllValues().get(1).getInstructions();

            assertThat(m2).hasSize(4);
            assertThat(m2.get(0).getText()).isEqualTo(m1.get(0).getText());
            assertThat(m2.get(1).getText()).isEqualTo(m1.get(1).getText());
            assertThat(m2.get(2)).isInstanceOf(AssistantMessage.class);
            assertThat(m2.get(2).getText()).isEqualTo(JSON_V2_VALIDO);
            assertThat(m2.get(3)).isInstanceOf(UserMessage.class);
            assertThat(m2.get(3).getText()).contains("NORMALIZACAO_V2_SEGUNDA");
        }
    }
}
