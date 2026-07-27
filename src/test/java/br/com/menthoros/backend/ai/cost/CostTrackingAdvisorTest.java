package br.com.menthoros.backend.ai.cost;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import br.com.menthoros.backend.config.external.LlmRoutingProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;
import java.util.Map;

class CostTrackingAdvisorTest {

    private SimpleMeterRegistry meterRegistry;
    private LlmPricingRegistry pricing;
    private ChatClientRequest request;
    private CallAdvisorChain chain;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        pricing = new LlmPricingRegistry(routingVigente());
        request = mock(ChatClientRequest.class);
        chain = mock(CallAdvisorChain.class);
    }

    private static LlmRoutingProperties.RotaLlm rota(String model) {
        LlmRoutingProperties.RotaLlm rota = new LlmRoutingProperties.RotaLlm();
        rota.setModel(model);
        rota.setTemperature(0.2);
        rota.setMaxTokens(1000);
        return rota;
    }

    private static LlmRoutingProperties routingVigente() {
        LlmRoutingProperties props = new LlmRoutingProperties();
        props.setSimple(rota("gpt-4o-mini"));
        props.setStandard(rota("claude-haiku-4-5-20251001"));
        props.setComplex(rota("claude-sonnet-4-6"));
        props.setExpert(rota("gpt-4o"));
        props.setPlano(rota("gpt-4o"));
        return props;
    }

    private static ChatClientResponse respostaCom(Usage usage, String model) {
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .usage(usage)
                .model(model)
                .build();
        ChatResponse chatResponse = new ChatResponse(
                List.of(new Generation(new AssistantMessage("ok"))), metadata);
        return new ChatClientResponse(chatResponse, Map.of());
    }

    private double contador(String nome, String model, String rota) {
        var counter = meterRegistry.find(nome).tags("model", model, "route", rota).counter();
        return counter != null ? counter.count() : 0.0;
    }

    @Nested
    @DisplayName("latência e timeout")
    class LatenciaETimeout {

        @Test
        @DisplayName("chamada concluída registra a duração num Timer por rota (CA7)")
        void registraDuracaoPorRota() {
            CostTrackingAdvisor advisor = CostTrackingAdvisor.paraRota("plano", pricing, meterRegistry);
            when(chain.nextCall(request)).thenReturn(
                    respostaCom(new DefaultUsage(10, 5), "gpt-4o"));

            advisor.adviseCall(request, chain);

            var timer = meterRegistry.find("llm.call.duration").tags("route", "plano").timer();
            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(1L);
        }

        @Test
        @DisplayName("timeout incrementa contador da rota e propaga a exceção")
        void timeoutContaEPropaga() {
            CostTrackingAdvisor advisor = CostTrackingAdvisor.paraRota("simple", pricing, meterRegistry);
            when(chain.nextCall(request)).thenThrow(new ResourceAccessException(
                    "timeout", new java.net.SocketTimeoutException("Read timed out")));

            assertThatThrownBy(() -> advisor.adviseCall(request, chain))
                    .isInstanceOf(ResourceAccessException.class);

            var counter = meterRegistry.find("llm.timeout").tags("route", "simple").counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("erro que não é timeout não incrementa o contador de timeout")
        void erroComumNaoContaComoTimeout() {
            CostTrackingAdvisor advisor = CostTrackingAdvisor.paraRota("simple", pricing, meterRegistry);
            when(chain.nextCall(request)).thenThrow(new IllegalStateException("boom"));

            assertThatThrownBy(() -> advisor.adviseCall(request, chain))
                    .isInstanceOf(IllegalStateException.class);

            assertThat(meterRegistry.find("llm.timeout").tags("route", "simple").counter()).isNull();
        }

        @Test
        @DisplayName("duração é registrada mesmo quando a chamada falha")
        void registraDuracaoNaFalha() {
            CostTrackingAdvisor advisor = CostTrackingAdvisor.paraRota("expert", pricing, meterRegistry);
            when(chain.nextCall(request)).thenThrow(new IllegalStateException("boom"));

            assertThatThrownBy(() -> advisor.adviseCall(request, chain)).isNotNull();

            var timer = meterRegistry.find("llm.call.duration").tags("route", "expert").timer();
            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("adviseCall")
    class AdviseCall {

        @Test
        @DisplayName("OpenAI: separa tokens cacheados do input e calcula custo com tarifa de cache read")
        void registraUsageOpenAi() {
            // prompt=100 (40 cacheados), completion=50
            OpenAiApi.Usage nativo = new OpenAiApi.Usage(50, 100, 150,
                    new OpenAiApi.Usage.PromptTokensDetails(0, 40), null);
            Usage usage = new DefaultUsage(100, 50, 150, nativo);
            when(chain.nextCall(any())).thenReturn(respostaCom(usage, "gpt-4o"));

            CostTrackingAdvisor advisor = CostTrackingAdvisor.paraRota("expert", pricing, meterRegistry);
            advisor.adviseCall(request, chain);

            assertThat(contador("llm.tokens.input", "gpt-4o", "expert")).isEqualTo(60.0);
            assertThat(contador("llm.tokens.output", "gpt-4o", "expert")).isEqualTo(50.0);
            assertThat(contador("llm.cache.read.tokens", "gpt-4o", "expert")).isEqualTo(40.0);
            assertThat(contador("llm.cache.write.tokens", "gpt-4o", "expert")).isEqualTo(0.0);
            // 60*2.50 + 40*1.25 + 50*10.00 = 700 por MTok -> 0.0007 USD
            assertThat(contador("llm.cost.estimated.usd", "gpt-4o", "expert"))
                    .isCloseTo(0.0007, within(1e-9));
        }

        @Test
        @DisplayName("Anthropic: registra cache read/write do usage nativo e tarifa de cache write")
        void registraUsageAnthropic() {
            // input=80 (exclui cache), output=30, cacheWrite=20, cacheRead=60
            AnthropicApi.Usage nativo = new AnthropicApi.Usage(80, 30, 20, 60);
            Usage usage = new DefaultUsage(140, 30, 170, nativo);
            when(chain.nextCall(any())).thenReturn(respostaCom(usage, "claude-sonnet-4-6"));

            CostTrackingAdvisor advisor = CostTrackingAdvisor.paraRota("complex", pricing, meterRegistry);
            advisor.adviseCall(request, chain);

            assertThat(contador("llm.tokens.input", "claude-sonnet-4-6", "complex")).isEqualTo(80.0);
            assertThat(contador("llm.tokens.output", "claude-sonnet-4-6", "complex")).isEqualTo(30.0);
            assertThat(contador("llm.cache.read.tokens", "claude-sonnet-4-6", "complex")).isEqualTo(60.0);
            assertThat(contador("llm.cache.write.tokens", "claude-sonnet-4-6", "complex")).isEqualTo(20.0);
            // 80*3.00 + 60*0.30 + 20*6.00 + 30*15.00 = 828 por MTok -> 0.000828 USD
            assertThat(contador("llm.cost.estimated.usd", "claude-sonnet-4-6", "complex"))
                    .isCloseTo(0.000828, within(1e-9));
        }

        @Test
        @DisplayName("devolve a resposta da chain intacta")
        void devolveRespostaIntacta() {
            ChatClientResponse resposta = respostaCom(new DefaultUsage(10, 5), "gpt-4o");
            when(chain.nextCall(any())).thenReturn(resposta);

            CostTrackingAdvisor advisor = CostTrackingAdvisor.paraRota("expert", pricing, meterRegistry);

            assertThat(advisor.adviseCall(request, chain)).isSameAs(resposta);
        }

        @Test
        @DisplayName("resposta sem chatResponse não incrementa nada nem lança")
        void ignoraRespostaSemChatResponse() {
            when(chain.nextCall(any())).thenReturn(new ChatClientResponse(null, Map.of()));

            CostTrackingAdvisor advisor = CostTrackingAdvisor.paraRota("expert", pricing, meterRegistry);

            assertThatCode(() -> advisor.adviseCall(request, chain)).doesNotThrowAnyException();

            // Nenhuma métrica de custo/token — não havia usage para extrair.
            // A duração é exceção deliberada (4.1): a chamada aconteceu e o tempo
            // dela é dado real, independente de dar para ler o usage.
            assertThat(meterRegistry.getMeters())
                    .extracting(m -> m.getId().getName())
                    .containsExactly("llm.call.duration");
        }

        @Test
        @DisplayName("modelo sem preço registra tokens mas não custo, sem quebrar a chamada")
        void modeloSemPrecoNaoQuebra() {
            Usage usage = new DefaultUsage(10, 5);
            when(chain.nextCall(any())).thenReturn(respostaCom(usage, "modelo-desconhecido"));

            CostTrackingAdvisor advisor = CostTrackingAdvisor.paraRota("expert", pricing, meterRegistry);

            assertThatCode(() -> advisor.adviseCall(request, chain)).doesNotThrowAnyException();
            assertThat(contador("llm.tokens.input", "modelo-desconhecido", "expert")).isEqualTo(10.0);
            assertThat(meterRegistry.find("llm.cost.estimated.usd").counter()).isNull();
        }

        @Test
        @DisplayName("usage sem native usage registra input/output e zera cache")
        void usageSemNativo() {
            Usage usage = new DefaultUsage(10, 5);
            when(chain.nextCall(any())).thenReturn(respostaCom(usage, "gpt-4o"));

            CostTrackingAdvisor advisor = CostTrackingAdvisor.paraRota("expert", pricing, meterRegistry);
            advisor.adviseCall(request, chain);

            assertThat(contador("llm.tokens.input", "gpt-4o", "expert")).isEqualTo(10.0);
            assertThat(contador("llm.tokens.output", "gpt-4o", "expert")).isEqualTo(5.0);
            assertThat(contador("llm.cache.read.tokens", "gpt-4o", "expert")).isEqualTo(0.0);
        }
    }
}
