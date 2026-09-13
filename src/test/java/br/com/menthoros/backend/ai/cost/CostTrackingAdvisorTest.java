package br.com.menthoros.backend.ai.cost;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import br.com.menthoros.backend.ai.ledger.LlmCallContext;
import br.com.menthoros.backend.ai.ledger.LlmCallRegistro;
import br.com.menthoros.backend.ai.ledger.LlmCallResult;
import br.com.menthoros.backend.ai.ledger.LlmCallScope;
import br.com.menthoros.backend.config.external.LlmRoutingProperties;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.services.helper.LlmCallLedger;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.mockito.ArgumentCaptor;
import java.util.Optional;
import java.util.UUID;
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
    private LlmCallLedger ledger;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        pricing = new LlmPricingRegistry(routingVigente());
        request = mock(ChatClientRequest.class);
        chain = mock(CallAdvisorChain.class);
        ledger = mock(LlmCallLedger.class);
        when(ledger.registrarChamada(any())).thenReturn(Optional.of(UUID.randomUUID()));
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
            CostTrackingAdvisor advisor = CostTrackingAdvisor.paraRota("plano", pricing, meterRegistry, ledger);
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
            CostTrackingAdvisor advisor = CostTrackingAdvisor.paraRota("simple", pricing, meterRegistry, ledger);
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
            CostTrackingAdvisor advisor = CostTrackingAdvisor.paraRota("simple", pricing, meterRegistry, ledger);
            when(chain.nextCall(request)).thenThrow(new IllegalStateException("boom"));

            assertThatThrownBy(() -> advisor.adviseCall(request, chain))
                    .isInstanceOf(IllegalStateException.class);

            assertThat(meterRegistry.find("llm.timeout").tags("route", "simple").counter()).isNull();
        }

        @Test
        @DisplayName("duração é registrada mesmo quando a chamada falha")
        void registraDuracaoNaFalha() {
            CostTrackingAdvisor advisor = CostTrackingAdvisor.paraRota("expert", pricing, meterRegistry, ledger);
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

            CostTrackingAdvisor advisor = CostTrackingAdvisor.paraRota("expert", pricing, meterRegistry, ledger);
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

            CostTrackingAdvisor advisor = CostTrackingAdvisor.paraRota("complex", pricing, meterRegistry, ledger);
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

            CostTrackingAdvisor advisor = CostTrackingAdvisor.paraRota("expert", pricing, meterRegistry, ledger);

            assertThat(advisor.adviseCall(request, chain)).isSameAs(resposta);
        }

        @Test
        @DisplayName("resposta sem chatResponse não incrementa nada nem lança")
        void ignoraRespostaSemChatResponse() {
            when(chain.nextCall(any())).thenReturn(new ChatClientResponse(null, Map.of()));

            CostTrackingAdvisor advisor = CostTrackingAdvisor.paraRota("expert", pricing, meterRegistry, ledger);

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

            CostTrackingAdvisor advisor = CostTrackingAdvisor.paraRota("expert", pricing, meterRegistry, ledger);

            assertThatCode(() -> advisor.adviseCall(request, chain)).doesNotThrowAnyException();
            assertThat(contador("llm.tokens.input", "modelo-desconhecido", "expert")).isEqualTo(10.0);
            assertThat(meterRegistry.find("llm.cost.estimated.usd").counter()).isNull();
        }

        @Test
        @DisplayName("usage sem native usage registra input/output e zera cache")
        void usageSemNativo() {
            Usage usage = new DefaultUsage(10, 5);
            when(chain.nextCall(any())).thenReturn(respostaCom(usage, "gpt-4o"));

            CostTrackingAdvisor advisor = CostTrackingAdvisor.paraRota("expert", pricing, meterRegistry, ledger);
            advisor.adviseCall(request, chain);

            assertThat(contador("llm.tokens.input", "gpt-4o", "expert")).isEqualTo(10.0);
            assertThat(contador("llm.tokens.output", "gpt-4o", "expert")).isEqualTo(5.0);
            assertThat(contador("llm.cache.read.tokens", "gpt-4o", "expert")).isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("ledger")
    class Ledger {

        private static final UUID TENANT = UUID.randomUUID();
        private static final UUID REQ = UUID.randomUUID();

        @AfterEach
        void limparEscopos() {
            LlmCallScope.closeRequest();
            TenantContext.clear();
        }

        private LlmCallRegistro registroGravado() {
            ArgumentCaptor<LlmCallRegistro> captor = ArgumentCaptor.forClass(LlmCallRegistro.class);
            verify(ledger).registrarChamada(captor.capture());
            return captor.getValue();
        }

        private void abrirEscopoPlano() {
            LlmCallScope.openRequest(REQ, UUID.randomUUID(), "Maria Souza");
            LlmCallScope.openAttempt(2, "plano-v1", "hash", "schema-v1");
        }

        @Test
        @DisplayName("sem escopo: linha genérica SUCCESS, sem contexto nem texto, com tokens, custo e latência")
        void semEscopoGravaGenerico() {
            TenantContext.setTenantId(TENANT);
            OpenAiApi.Usage nativo = new OpenAiApi.Usage(50, 100, 150,
                    new OpenAiApi.Usage.PromptTokensDetails(0, 40), null);
            when(chain.nextCall(any())).thenReturn(respostaCom(new DefaultUsage(100, 50, 150, nativo), "gpt-4o"));

            CostTrackingAdvisor.paraRota("expert", pricing, meterRegistry, ledger).adviseCall(request, chain);

            LlmCallRegistro r = registroGravado();
            assertThat(r.route()).isEqualTo("expert");
            assertThat(r.model()).isEqualTo("gpt-4o");
            assertThat(r.result()).isEqualTo(LlmCallResult.SUCCESS);
            assertThat(r.tenantId()).isEqualTo(TENANT);
            assertThat(r.inputTokens()).isEqualTo(60L);
            assertThat(r.outputTokens()).isEqualTo(50L);
            assertThat(r.cacheReadTokens()).isEqualTo(40L);
            assertThat(r.cacheWriteTokens()).isEqualTo(0L);
            assertThat(r.costUsd()).isEqualByComparingTo("0.0007");
            assertThat(r.latencyMs()).isGreaterThanOrEqualTo(0);
            assertThat(r.context()).isEmpty();
            assertThat(r.responseText()).isNull();
            assertThat(r.transportRetries()).isNull();
        }

        @Test
        @DisplayName("com escopo da rota plano: nasce PENDING, com contexto, texto bruto, retries e id registrado")
        void comEscopoGravaPendingEnriquecido() {
            TenantContext.setTenantId(TENANT);
            abrirEscopoPlano();
            LlmCallScope.incrementTransportRetry();
            LlmCallScope.incrementTransportRetry();
            UUID id = UUID.randomUUID();
            when(ledger.registrarChamada(any())).thenReturn(Optional.of(id));
            when(chain.nextCall(any())).thenReturn(respostaCom(new DefaultUsage(10, 5), "gpt-4o"));

            CostTrackingAdvisor.paraRota("plano", pricing, meterRegistry, ledger).adviseCall(request, chain);

            LlmCallRegistro r = registroGravado();
            assertThat(r.result()).isEqualTo(LlmCallResult.PENDING);
            assertThat(r.responseText()).isEqualTo("ok");
            assertThat(r.transportRetries()).isEqualTo(2);
            LlmCallContext ctx = r.context().orElseThrow();
            assertThat(ctx.generationRequestId()).isEqualTo(REQ);
            assertThat(ctx.attempt()).isEqualTo(2);
            assertThat(ctx.atletaNome()).isEqualTo("Maria Souza");
            assertThat(LlmCallScope.lastCallId()).contains(id);
        }

        @Test
        @DisplayName("timeout grava TIMEOUT sem tokens, modelo desconhecido, e propaga")
        void timeoutGravaLinha() {
            abrirEscopoPlano();
            when(chain.nextCall(request)).thenThrow(new ResourceAccessException(
                    "timeout", new java.net.SocketTimeoutException("Read timed out")));

            assertThatThrownBy(() -> CostTrackingAdvisor.paraRota("plano", pricing, meterRegistry, ledger)
                    .adviseCall(request, chain)).isInstanceOf(ResourceAccessException.class);

            LlmCallRegistro r = registroGravado();
            assertThat(r.result()).isEqualTo(LlmCallResult.TIMEOUT);
            assertThat(r.model()).isEqualTo("desconhecido");
            assertThat(r.inputTokens()).isNull();
            assertThat(r.costUsd()).isNull();
            assertThat(r.responseText()).isNull();
            assertThat(r.context()).isPresent();
        }

        @Test
        @DisplayName("erro que não é timeout grava LLM_ERROR e propaga")
        void erroComumGravaLlmError() {
            when(chain.nextCall(request)).thenThrow(new IllegalStateException("500 do provider"));

            assertThatThrownBy(() -> CostTrackingAdvisor.paraRota("simple", pricing, meterRegistry, ledger)
                    .adviseCall(request, chain)).isInstanceOf(IllegalStateException.class);

            assertThat(registroGravado().result()).isEqualTo(LlmCallResult.LLM_ERROR);
        }

        @Test
        @DisplayName("ledger devolvendo vazio deixa lastCallId vazio e a resposta segue")
        void ledgerVazioNaoAtrapalha() {
            abrirEscopoPlano();
            when(ledger.registrarChamada(any())).thenReturn(Optional.empty());
            ChatClientResponse esperada = respostaCom(new DefaultUsage(1, 1), "gpt-4o");
            when(chain.nextCall(any())).thenReturn(esperada);

            ChatClientResponse resposta = CostTrackingAdvisor.paraRota("plano", pricing, meterRegistry, ledger)
                    .adviseCall(request, chain);

            assertThat(resposta).isSameAs(esperada);
            assertThat(LlmCallScope.lastCallId()).isEmpty();
        }

        @Test
        @DisplayName("ledger lançando não derruba a chamada (CA8)")
        void ledgerLancandoNaoDerruba() {
            when(ledger.registrarChamada(any())).thenThrow(new RuntimeException("ledger quebrado"));
            ChatClientResponse esperada = respostaCom(new DefaultUsage(1, 1), "gpt-4o");
            when(chain.nextCall(any())).thenReturn(esperada);

            assertThatCode(() -> CostTrackingAdvisor.paraRota("plano", pricing, meterRegistry, ledger)
                    .adviseCall(request, chain)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("resposta sem chatResponse ainda vira linha SUCCESS com modelo desconhecido e sem tokens")
        void respostaSemCorpo() {
            when(chain.nextCall(any())).thenReturn(new ChatClientResponse(null, Map.of()));

            CostTrackingAdvisor.paraRota("expert", pricing, meterRegistry, ledger).adviseCall(request, chain);

            LlmCallRegistro r = registroGravado();
            assertThat(r.result()).isEqualTo(LlmCallResult.SUCCESS);
            assertThat(r.model()).isEqualTo("desconhecido");
            assertThat(r.inputTokens()).isNull();
        }

        @Test
        @DisplayName("resposta com metadata sem usage explícito: o Spring AI entrega usage zerado, e o registro grava 0, não nulo")
        void metadataSemUsage() {
            ChatResponse chatResponse = new ChatResponse(
                    List.of(new Generation(new AssistantMessage("ok"))),
                    ChatResponseMetadata.builder().model("gpt-4o").build());
            when(chain.nextCall(any())).thenReturn(new ChatClientResponse(chatResponse, Map.of()));

            CostTrackingAdvisor.paraRota("expert", pricing, meterRegistry, ledger).adviseCall(request, chain);

            LlmCallRegistro r = registroGravado();
            assertThat(r.model()).isEqualTo("gpt-4o");
            assertThat(r.inputTokens()).isZero();
            assertThat(r.outputTokens()).isZero();
            assertThat(r.costUsd()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("modelo sem preço grava tokens e custo nulo")
        void semPrecoCustoNulo() {
            when(chain.nextCall(any())).thenReturn(respostaCom(new DefaultUsage(10, 5), "modelo-desconhecido"));

            CostTrackingAdvisor.paraRota("expert", pricing, meterRegistry, ledger).adviseCall(request, chain);

            LlmCallRegistro r = registroGravado();
            assertThat(r.inputTokens()).isEqualTo(10L);
            assertThat(r.costUsd()).isNull();
        }

        @Test
        @DisplayName("Anthropic: cache write chega ao registro")
        void anthropicCacheWrite() {
            // Ordem do record: input, output, cacheCreation (write), cacheRead
            AnthropicApi.Usage nativo = new AnthropicApi.Usage(100, 50, 30, 20);
            when(chain.nextCall(any())).thenReturn(respostaCom(new DefaultUsage(100, 50, 150, nativo), "claude-sonnet-4-6"));

            CostTrackingAdvisor.paraRota("complex", pricing, meterRegistry, ledger).adviseCall(request, chain);

            LlmCallRegistro r = registroGravado();
            assertThat(r.cacheReadTokens()).isEqualTo(20L);
            assertThat(r.cacheWriteTokens()).isEqualTo(30L);
        }

        @Test
        @DisplayName("sem tenant na thread, o registro vai com tenant nulo")
        void tenantNulo() {
            when(chain.nextCall(any())).thenReturn(respostaCom(new DefaultUsage(1, 1), "gpt-4o"));

            CostTrackingAdvisor.paraRota("standard", pricing, meterRegistry, ledger).adviseCall(request, chain);

            assertThat(registroGravado().tenantId()).isNull();
        }

        @Test
        @DisplayName("resposta com escopo mas sem texto de saída não quebra")
        void escopoSemTexto() {
            abrirEscopoPlano();
            ChatResponse semGeneration = new ChatResponse(List.of(),
                    ChatResponseMetadata.builder().usage(new DefaultUsage(1, 1)).model("gpt-4o").build());
            when(chain.nextCall(any())).thenReturn(new ChatClientResponse(semGeneration, Map.of()));

            assertThatCode(() -> CostTrackingAdvisor.paraRota("plano", pricing, meterRegistry, ledger)
                    .adviseCall(request, chain)).doesNotThrowAnyException();
            assertThat(registroGravado().responseText()).isNull();
        }

        @Test
        @DisplayName("tag tenant sempre presente: none primeiro e tenant depois registram no Prometheus real (CA11)")
        void tagTenantNonePrimeiro() {
            var prometheus = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
            CostTrackingAdvisor advisor = CostTrackingAdvisor.paraRota("expert", pricing, prometheus, ledger);
            when(chain.nextCall(any())).thenReturn(respostaCom(new DefaultUsage(10, 5), "gpt-4o"));

            advisor.adviseCall(request, chain);
            TenantContext.setTenantId(TENANT);
            advisor.adviseCall(request, chain);

            assertThat(prometheus.find("llm.cost.estimated.usd").tags("tenant", "none").counter()).isNotNull();
            assertThat(prometheus.find("llm.cost.estimated.usd").tags("tenant", TENANT.toString()).counter()).isNotNull();
            assertThat(prometheus.scrape()).contains("tenant=\"none\"").contains("tenant=\"" + TENANT + "\"");
        }

        @Test
        @DisplayName("tag tenant sempre presente: tenant primeiro e none depois também registram (CA11)")
        void tagTenantTenantPrimeiro() {
            var prometheus = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
            CostTrackingAdvisor advisor = CostTrackingAdvisor.paraRota("expert", pricing, prometheus, ledger);
            when(chain.nextCall(any())).thenReturn(respostaCom(new DefaultUsage(10, 5), "gpt-4o"));

            TenantContext.setTenantId(TENANT);
            advisor.adviseCall(request, chain);
            TenantContext.clear();
            advisor.adviseCall(request, chain);

            assertThat(prometheus.find("llm.cost.estimated.usd").tags("tenant", TENANT.toString()).counter().count())
                    .isCloseTo(0.000075, within(1e-9));
            assertThat(prometheus.find("llm.cost.estimated.usd").tags("tenant", "none").counter().count())
                    .isCloseTo(0.000075, within(1e-9));
        }
    }
}
