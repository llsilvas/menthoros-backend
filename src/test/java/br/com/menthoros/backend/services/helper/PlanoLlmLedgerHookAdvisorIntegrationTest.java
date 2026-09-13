package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.ai.cost.CostTrackingAdvisor;
import br.com.menthoros.backend.ai.cost.LlmPricingRegistry;
import br.com.menthoros.backend.ai.ledger.LlmCallResult;
import br.com.menthoros.backend.ai.ledger.LlmCallScope;
import br.com.menthoros.backend.ai.ledger.PromptHashCalculator;
import br.com.menthoros.backend.config.external.LlmRoutingProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.web.client.ResourceAccessException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * `PlanoLlmLedgerHook.Sessao` + `CostTrackingAdvisor` **reais**, mockando só `LlmCallLedger` — a
 * exata fronteira onde um bug real escapou dos testes isolados (add-plan-generation-ledger, `/qa`):
 * o advisor registrava o {@code callId} no escopo também no caminho de exceção, e o hook lia esse
 * id e sobrescrevia a linha {@code TIMEOUT}/{@code LLM_ERROR} para {@code PARSE_ERROR}. Cada peça
 * isolada (`CostTrackingAdvisorTest`, `PlanoLlmLedgerHookTest`) simulava a vizinha de um jeito que
 * não reproduzia o contrato real — só um teste que atravessa as duas pega isso.
 */
@ExtendWith(MockitoExtension.class)
class PlanoLlmLedgerHookAdvisorIntegrationTest {

    @Mock
    private LlmCallLedger ledger;
    @Mock
    private PromptHashCalculator promptHash;
    @Mock
    private ChatClientRequest request;
    @Mock
    private CallAdvisorChain chain;

    private final LlmPricingRegistry pricing = new LlmPricingRegistry(routingVigente());
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private static LlmRoutingProperties routingVigente() {
        var props = new LlmRoutingProperties();
        var plano = new LlmRoutingProperties.RotaLlm();
        plano.setModel("gpt-4o");
        plano.setTemperature(0.2);
        plano.setMaxTokens(12000);
        props.setPlano(plano);
        return props;
    }

    private PlanoLlmLedgerHook.Sessao sessaoComAdvisorReal() {
        when(promptHash.valor()).thenReturn("hash");
        // Reproduz PlanoServiceImpl.gerarPlanoSemanal: abre a requisição antes de a Sessao
        // abrir a tentativa — sem isso LlmCallScope.current() fica vazio (sem request) e o
        // advisor nunca decide PENDING, mesmo com a tentativa aberta.
        LlmCallScope.openRequest(UUID.randomUUID(), null, null);
        return new PlanoLlmLedgerHook(ledger, promptHash).novaSessao();
    }

    private CostTrackingAdvisor advisorReal() {
        return CostTrackingAdvisor.paraRota("plano", pricing, meterRegistry, ledger);
    }

    @AfterEach
    void limparEscopo() {
        LlmCallScope.closeRequest();
    }

    @Test
    @DisplayName("timeout do provider fecha a linha como TIMEOUT — o hook NÃO sobrescreve para PARSE_ERROR")
    void timeoutDoProviderNaoViraParseError() {
        UUID idDaLinhaDeErro = UUID.randomUUID();
        when(ledger.registrarChamada(any())).thenReturn(java.util.Optional.of(idDaLinhaDeErro));
        when(chain.nextCall(request)).thenThrow(new ResourceAccessException(
                "timeout", new java.net.SocketTimeoutException("Read timed out")));

        var sessao = sessaoComAdvisorReal();
        CostTrackingAdvisor advisor = advisorReal();

        assertThatThrownBy(() -> sessao.chamar(1, () -> advisor.adviseCall(request, chain)))
                .isInstanceOf(ResourceAccessException.class);

        // A linha correta (TIMEOUT) foi gravada uma única vez pelo advisor...
        verify(ledger).registrarChamada(argThat(r -> r.result() == LlmCallResult.TIMEOUT));
        // ...e o hook nunca a sobrescreve para PARSE_ERROR (o bug real fazia exatamente isso).
        verify(ledger, never()).registrarResultado(any(), eq(LlmCallResult.PARSE_ERROR), any());
        assertThat(sessao.ultimaChamada()).as("sem chamada pendente de validação após erro terminal").isEmpty();
    }

    @Test
    @DisplayName("erro genérico do provider fecha a linha como LLM_ERROR — o hook NÃO sobrescreve para PARSE_ERROR")
    void erroGenericoNaoViraParseError() {
        when(ledger.registrarChamada(any())).thenReturn(java.util.Optional.of(UUID.randomUUID()));
        when(chain.nextCall(request)).thenThrow(new IllegalStateException("503 do provider"));

        var sessao = sessaoComAdvisorReal();
        CostTrackingAdvisor advisor = advisorReal();

        assertThatThrownBy(() -> sessao.chamar(1, () -> advisor.adviseCall(request, chain)))
                .isInstanceOf(IllegalStateException.class);

        verify(ledger).registrarChamada(argThat(r -> r.result() == LlmCallResult.LLM_ERROR));
        verify(ledger, never()).registrarResultado(any(), eq(LlmCallResult.PARSE_ERROR), any());
    }

    @Test
    @DisplayName("conversão falhando depois de HTTP 200 — este sim vira PARSE_ERROR")
    void conversaoFalhandoDepoisDoHttp200VeraParseError() {
        UUID idDaLinhaPending = UUID.randomUUID();
        when(ledger.registrarChamada(any())).thenReturn(java.util.Optional.of(idDaLinhaPending));
        when(chain.nextCall(request)).thenReturn(mock(ChatClientResponse.class)); // resposta HTTP ok

        var sessao = sessaoComAdvisorReal();
        CostTrackingAdvisor advisor = advisorReal();

        assertThatThrownBy(() -> sessao.chamar(1, () -> {
            advisor.adviseCall(request, chain); // grava PENDING, registra o id no escopo
            throw new IllegalStateException("Cannot deserialize PlanoSemanalLlmDto"); // parse falha depois
        })).isInstanceOf(IllegalStateException.class);

        verify(ledger).registrarChamada(argThat(r -> r.result() == LlmCallResult.PENDING));
        verify(ledger).registrarResultado(eq(idDaLinhaPending), eq(LlmCallResult.PARSE_ERROR), any());
    }

    // Predicado legível para verify(...).registrarChamada(argThat(...)) sem importar Hamcrest.
    private static <T> T argThat(java.util.function.Predicate<T> predicate) {
        return org.mockito.ArgumentMatchers.argThat(predicate::test);
    }
}
