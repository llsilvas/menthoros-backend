package br.com.menthoros.backend.config.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.menthoros.backend.ai.cost.LlmPricingRegistry;
import br.com.menthoros.backend.services.helper.LlmCallLedger;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
 * Task 0.1 de {@code plan-generation-repair-turn}: dupla checagem contra o {@link ChatClient}
 * real — cadeia de advisors default incluída ({@code ChatModelCallAdvisor}), só o
 * {@link org.springframework.ai.chat.model.ChatModel} é mockado (mesma costura de
 * {@link MultiModelConfigTest}, via {@link MultiModelConfig#clienteDeRota}).
 *
 * <p>Achado do pré-mortem (2026-09-14, 4 rodadas): {@code .call().responseEntity(Class)} embrulha
 * em {@code BeanOutputConverter}, que grava {@code ChatClientAttributes.OUTPUT_FORMAT} no
 * contexto — e {@code ChatModelCallAdvisor.augmentWithFormatInstructions} injeta esse texto na
 * <b>última</b> {@code UserMessage} da conversa (confirmado por bytecode de
 * {@code spring-ai-client-chat-1.1.6.jar}). Isso quebraria a igualdade de prefixo entre a 1ª
 * tentativa (onde a última mensagem é o {@code user} original) e o turno de reparo (onde a
 * última passa a ser a mensagem de correção). A correção foi abandonar {@code .responseEntity} em
 * favor de {@code .call().content()} — este teste prova que, sem ele, nenhum advisor da cadeia
 * default toca as mensagens: o prefixo {@code system + user original} chega idêntico ao
 * {@link org.springframework.ai.chat.model.ChatModel} nas duas chamadas.</p>
 */
class ChatClientRepairTurnPrefixSpikeTest {

    @Test
    @DisplayName("prefixo system+user é byte-idêntico entre a 1ª tentativa e o turno de reparo, sem .responseEntity()")
    void prefixoIdenticoSemResponseEntity() {
        LlmRoutingProperties props = new LlmRoutingProperties();
        LlmRoutingProperties.RotaLlm rota = new LlmRoutingProperties.RotaLlm();
        rota.setModel("gpt-4o");
        rota.setTemperature(0.2);
        rota.setMaxTokens(12000);
        props.setPlano(rota);

        MultiModelConfig config = new MultiModelConfig(props, new LlmPricingRegistry(props),
                new SimpleMeterRegistry(), mock(LlmCallLedger.class));

        OpenAiChatModel chatModel = mock(OpenAiChatModel.class);
        ChatResponse resposta = new ChatResponse(
                List.of(new Generation(new AssistantMessage("{}"))),
                ChatResponseMetadata.builder().usage(new DefaultUsage(10, 5)).model("gpt-4o").build());
        when(chatModel.call(any(Prompt.class))).thenReturn(resposta);

        ChatClient chatClient = config.clienteDeRota(chatModel, MultiModelConfig.opcoesOpenAi(props.getPlano()), "plano");

        String system = "system fixo, idêntico entre tentativas (F1)";
        String userOriginal = "gere o plano da semana para o atleta X";

        // 1ª tentativa — sem .responseEntity()/.entity(), como a Decisão 1/3 revisadas exigem
        chatClient.prompt().system(system).user(userOriginal).call().content();

        // turno de reparo (2ª tentativa) — mensagens ACRESCENTADAS, nunca reescritas (Decisão 1)
        chatClient.prompt()
                .messages(List.of(
                        new SystemMessage(system),
                        new UserMessage(userOriginal),
                        new AssistantMessage("{\"treinosPlanejados\":[]}"),
                        new UserMessage("corrija exatamente isto: violação X, mantendo o resto")))
                .call().content();

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2)).call(captor.capture());
        List<Message> mensagens1aTentativa = captor.getAllValues().get(0).getInstructions();
        List<Message> mensagens2aTentativa = captor.getAllValues().get(1).getInstructions();

        assertThat(mensagens1aTentativa).hasSize(2);
        assertThat(mensagens2aTentativa).hasSize(4);

        assertThat(mensagens1aTentativa.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(mensagens2aTentativa.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(mensagens2aTentativa.get(0).getText()).isEqualTo(mensagens1aTentativa.get(0).getText());

        assertThat(mensagens1aTentativa.get(1)).isInstanceOf(UserMessage.class);
        assertThat(mensagens2aTentativa.get(1)).isInstanceOf(UserMessage.class);
        assertThat(mensagens2aTentativa.get(1).getText()).isEqualTo(mensagens1aTentativa.get(1).getText());

        assertThat(mensagens2aTentativa.get(2)).isInstanceOf(AssistantMessage.class);
        assertThat(mensagens2aTentativa.get(3)).isInstanceOf(UserMessage.class);
    }
}
