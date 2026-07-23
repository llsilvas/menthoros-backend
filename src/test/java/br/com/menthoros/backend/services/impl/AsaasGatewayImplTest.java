package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.exception.AsaasIntegrationException;
import br.com.menthoros.backend.services.AsaasGateway.AsaasAssinaturaCriada;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integração (WireMock) do {@link AsaasGatewayImpl} contra a API v3 do Asaas: prova o contrato HTTP
 * real (endpoints, header {@code access_token}, idempotência do cliente por {@code externalReference})
 * e que o token de cartão / API key nunca vazam em log ou mensagem de exceção.
 */
class AsaasGatewayImplTest {

    private static final String API_KEY = "$aact_hmlg_chave-secreta-nao-logavel";
    private static final String CARD_TOKEN = "tok_cartao_secreto_nao_logavel";

    private WireMockServer wireMock;
    private AsaasGatewayImpl gateway;
    private ListAppender<ILoggingEvent> logCapture;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(2).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(2).toMillis());
        RestClient restClient = RestClient.builder()
                .baseUrl(wireMock.baseUrl())
                .requestFactory(factory)
                .defaultHeader("access_token", API_KEY)
                .defaultHeader("User-Agent", "menthoros-backend")
                .build();
        gateway = new AsaasGatewayImpl(restClient);

        logCapture = new ListAppender<>();
        logCapture.start();
        ((Logger) LoggerFactory.getLogger("br.com.menthoros.backend")).addAppender(logCapture);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger("br.com.menthoros.backend")).detachAppender(logCapture);
        wireMock.stop();
    }

    @Nested
    @DisplayName("criarClienteEAssinatura")
    class CriarClienteEAssinatura {

        @Test
        @DisplayName("sem cliente prévio: cria customer e subscription e retorna os ids (CA1)")
        void criaClienteEAssinatura() {
            Assessoria assessoria = assessoria();
            stubBuscaCliente(assessoria.getId(), "{\"data\":[],\"totalCount\":0}");
            stubBuscaAssinatura(assessoria.getId(), "{\"data\":[],\"totalCount\":0}");
            wireMock.stubFor(post(urlEqualTo("/customers"))
                    .withHeader("access_token", equalTo(API_KEY))
                    .willReturn(okJson("{\"id\":\"cus_novo\"}")));
            wireMock.stubFor(post(urlEqualTo("/subscriptions"))
                    .willReturn(okJson("{\"id\":\"sub_123\",\"status\":\"ACTIVE\"}")));

            AsaasAssinaturaCriada resultado = gateway.criarClienteEAssinatura(
                    assessoria, CARD_TOKEN, LocalDate.of(2026, 9, 1), new BigDecimal("199.90"));

            assertThat(resultado.asaasCustomerId()).isEqualTo("cus_novo");
            assertThat(resultado.asaasSubscriptionId()).isEqualTo("sub_123");
            assertThat(resultado.status()).isEqualTo("ACTIVE");
            wireMock.verify(postRequestedFor(urlEqualTo("/subscriptions"))
                    .withRequestBody(matchingJsonPath("$.billingType", equalTo("CREDIT_CARD")))
                    .withRequestBody(matchingJsonPath("$.cycle", equalTo("MONTHLY")))
                    .withRequestBody(matchingJsonPath("$.creditCardToken", equalTo(CARD_TOKEN)))
                    .withRequestBody(matchingJsonPath("$.externalReference", equalTo(assessoria.getId().toString()))));
        }

        @Test
        @DisplayName("cliente já existe (externalReference): reaproveita e NÃO cria outro customer (CA14)")
        void reaproveitaClienteExistente() {
            Assessoria assessoria = assessoria();
            stubBuscaCliente(assessoria.getId(), "{\"data\":[{\"id\":\"cus_existente\"}],\"totalCount\":1}");
            stubBuscaAssinatura(assessoria.getId(), "{\"data\":[],\"totalCount\":0}");
            wireMock.stubFor(post(urlEqualTo("/subscriptions"))
                    .willReturn(okJson("{\"id\":\"sub_9\",\"status\":\"ACTIVE\"}")));

            AsaasAssinaturaCriada resultado = gateway.criarClienteEAssinatura(
                    assessoria, CARD_TOKEN, LocalDate.of(2026, 9, 1), new BigDecimal("199.90"));

            assertThat(resultado.asaasCustomerId()).isEqualTo("cus_existente");
            wireMock.verify(0, postRequestedFor(urlEqualTo("/customers")));
        }

        @Test
        @DisplayName("subscription já existe (retry sobre PENDENTE): reaproveita e NÃO cria outra (C2/CA14)")
        void reaproveitaAssinaturaExistente() {
            Assessoria assessoria = assessoria();
            stubBuscaCliente(assessoria.getId(), "{\"data\":[{\"id\":\"cus_existente\"}],\"totalCount\":1}");
            stubBuscaAssinatura(assessoria.getId(),
                    "{\"data\":[{\"id\":\"sub_existente\",\"status\":\"ACTIVE\"}],\"totalCount\":1}");

            AsaasAssinaturaCriada resultado = gateway.criarClienteEAssinatura(
                    assessoria, CARD_TOKEN, LocalDate.of(2026, 9, 1), new BigDecimal("199.90"));

            assertThat(resultado.asaasSubscriptionId()).isEqualTo("sub_existente");
            wireMock.verify(0, postRequestedFor(urlEqualTo("/subscriptions")));
        }

        @Test
        @DisplayName("resposta não-2xx do Asaas lança AsaasIntegrationException")
        void erroDoAsaasLancaExcecao() {
            Assessoria assessoria = assessoria();
            stubBuscaCliente(assessoria.getId(), "{\"data\":[],\"totalCount\":0}");
            wireMock.stubFor(post(urlEqualTo("/customers"))
                    .willReturn(aResponse().withStatus(400).withBody("{\"errors\":[{\"description\":\"cpfCnpj inválido\"}]}")));

            assertThatThrownBy(() -> gateway.criarClienteEAssinatura(
                    assessoria, CARD_TOKEN, LocalDate.of(2026, 9, 1), new BigDecimal("10.00")))
                    .isInstanceOf(AsaasIntegrationException.class);
        }

        @Test
        @DisplayName("token de cartão e API key nunca aparecem em log nem na exceção")
        void segredosNuncaVazam() {
            Assessoria assessoria = assessoria();
            stubBuscaCliente(assessoria.getId(), "{\"data\":[],\"totalCount\":0}");
            stubBuscaAssinatura(assessoria.getId(), "{\"data\":[],\"totalCount\":0}");
            wireMock.stubFor(post(urlEqualTo("/customers")).willReturn(okJson("{\"id\":\"cus_x\"}")));
            wireMock.stubFor(post(urlEqualTo("/subscriptions"))
                    .willReturn(aResponse().withStatus(500).withBody("boom")));

            assertThatThrownBy(() -> gateway.criarClienteEAssinatura(
                    assessoria, CARD_TOKEN, LocalDate.of(2026, 9, 1), new BigDecimal("10.00")))
                    .isInstanceOf(AsaasIntegrationException.class)
                    .satisfies(e -> {
                        assertThat(e.getMessage()).doesNotContain(CARD_TOKEN).doesNotContain(API_KEY);
                        assertThat(String.valueOf(e.getCause())).doesNotContain(CARD_TOKEN).doesNotContain(API_KEY);
                    });

            String logs = logCapture.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .reduce("", (a, b) -> a + "\n" + b);
            assertThat(logs).doesNotContain(CARD_TOKEN).doesNotContain(API_KEY);
        }
    }

    @Nested
    @DisplayName("atualizarValor")
    class AtualizarValor {

        @Test
        @DisplayName("PUT /subscriptions/{id} com value e updatePendingPayments (CA9)")
        void atualizaValor() {
            wireMock.stubFor(put(urlEqualTo("/subscriptions/sub_123")).willReturn(okJson("{\"id\":\"sub_123\"}")));

            gateway.atualizarValor("sub_123", new BigDecimal("299.90"));

            wireMock.verify(putRequestedFor(urlEqualTo("/subscriptions/sub_123"))
                    .withRequestBody(equalToJson("{\"value\": 299.90, \"updatePendingPayments\": true}", true, true)));
        }

        @Test
        @DisplayName("não-2xx lança AsaasIntegrationException")
        void erroLancaExcecao() {
            wireMock.stubFor(put(urlEqualTo("/subscriptions/sub_x")).willReturn(aResponse().withStatus(404)));

            assertThatThrownBy(() -> gateway.atualizarValor("sub_x", new BigDecimal("1.00")))
                    .isInstanceOf(AsaasIntegrationException.class);
        }
    }

    @Nested
    @DisplayName("cancelarAssinatura")
    class CancelarAssinatura {

        @Test
        @DisplayName("DELETE /subscriptions/{id} (CA7)")
        void cancela() {
            wireMock.stubFor(delete(urlEqualTo("/subscriptions/sub_123"))
                    .willReturn(okJson("{\"deleted\":true,\"id\":\"sub_123\"}")));

            gateway.cancelarAssinatura("sub_123");

            wireMock.verify(deleteRequestedFor(urlEqualTo("/subscriptions/sub_123")));
        }

        @Test
        @DisplayName("não-2xx lança AsaasIntegrationException")
        void erroLancaExcecao() {
            wireMock.stubFor(delete(urlEqualTo("/subscriptions/sub_x")).willReturn(aResponse().withStatus(500)));

            assertThatThrownBy(() -> gateway.cancelarAssinatura("sub_x"))
                    .isInstanceOf(AsaasIntegrationException.class);
        }
    }

    private void stubBuscaCliente(UUID assessoriaId, String jsonBody) {
        wireMock.stubFor(get(urlPathEqualTo("/customers"))
                .withQueryParam("externalReference", equalTo(assessoriaId.toString()))
                .willReturn(okJson(jsonBody)));
    }

    private void stubBuscaAssinatura(UUID assessoriaId, String jsonBody) {
        wireMock.stubFor(get(urlPathEqualTo("/subscriptions"))
                .withQueryParam("externalReference", equalTo(assessoriaId.toString()))
                .willReturn(okJson(jsonBody)));
    }

    private Assessoria assessoria() {
        Assessoria assessoria = new Assessoria();
        assessoria.setId(UUID.randomUUID());
        assessoria.setNome("Team X");
        assessoria.setRazaoSocial("Team X Assessoria Esportiva LTDA");
        assessoria.setCnpj("12.345.678/0001-90");
        assessoria.setEmailContato("financeiro@teamx.com");
        return assessoria;
    }
}
