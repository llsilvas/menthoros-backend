package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.config.external.IntervalsIcuProperties;
import br.com.menthoros.backend.config.external.IntervalsIcuWebClientConfig;
import br.com.menthoros.backend.exception.IntervalsIcuApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Prova o item 8.3 da spec (responseTimeout de 10s libera a thread do pool de push):
 * usa o bean REAL de {@link IntervalsIcuWebClientConfig} (connect 5s / responseTimeout 10s,
 * mesma config de produção) apontado para um WireMock que atrasa a resposta em 11s — acima
 * do timeout. A chamada deve lançar {@link IntervalsIcuApiException} de transporte
 * (status nulo) antes dos 11s, provando que a thread não fica pendurada indefinidamente.
 *
 * <p>Sem contexto Spring — instancia {@code IntervalsIcuWebClientConfig} e
 * {@code IntervalsIcuClientImpl} diretamente, mantendo o teste leve e estável.
 */
class IntervalsIcuTimeoutTest {

    private static final String API_KEY = "chave-timeout-test";

    private WireMockServer wireMock;
    private IntervalsIcuClientImpl client;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();

        IntervalsIcuProperties properties = new IntervalsIcuProperties();
        properties.setBaseUrl(wireMock.baseUrl());

        // bean REAL da Task 2 (config de produção) — não um WebClient de teste sem timeout
        WebClient webClient = new IntervalsIcuWebClientConfig(properties).intervalsIcuWebClient();
        client = new IntervalsIcuClientImpl(webClient);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    @DisplayName("responseTimeout de 10s do bean real libera a thread antes de 11s (item 8.3)")
    void responseTimeoutLiberaThreadAntesDe11Segundos() {
        wireMock.stubFor(post(urlEqualTo("/api/v1/athlete/i641775/events"))
                .willReturn(aResponse().withFixedDelay(11_000).withStatus(200)));

        assertTimeoutPreemptively(Duration.ofSeconds(11), () ->
                assertThatThrownBy(() -> client.criarEvento(API_KEY, "i641775",
                        new ObjectMapper().createObjectNode().put("category", "WORKOUT")))
                        .isInstanceOf(IntervalsIcuApiException.class)
                        .satisfies(e -> assertThat(((IntervalsIcuApiException) e).getStatus()).isNull()));
    }
}
