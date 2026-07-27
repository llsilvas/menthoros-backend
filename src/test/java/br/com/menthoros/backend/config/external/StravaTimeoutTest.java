package br.com.menthoros.backend.config.external;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Prova o CA5 da change {@code add-external-call-resilience}: com o Strava sem
 * responder, a busca de atividade falha em ~10s em vez de pendurar a thread.
 *
 * <p>Usa o bean REAL de {@link StravaWebClientConfig} (connect 5s / responseTimeout
 * 10s) contra um WireMock que atrasa 11s — acima do teto. Mesmo formato do
 * {@code IntervalsIcuTimeoutTest}, que já cobre a integração irmã.
 */
class StravaTimeoutTest {

    private WireMockServer wireMock;
    private WebClient stravaWebClient;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();

        StravaProperties properties = new StravaProperties();
        properties.setApiBaseUrl(wireMock.baseUrl());

        stravaWebClient = new StravaWebClientConfig(properties).stravaWebClient();
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    @DisplayName("responseTimeout de 10s libera a thread antes de 11s (CA5)")
    void responseTimeoutLiberaThreadAntesDe11Segundos() {
        wireMock.stubFor(get(urlPathMatching("/activities/.*"))
                .willReturn(aResponse().withFixedDelay(11_000).withStatus(200)));

        assertTimeoutPreemptively(Duration.ofSeconds(11), () ->
                assertThatThrownBy(() -> stravaWebClient.get()
                        .uri("/activities/123")
                        .retrieve()
                        .bodyToMono(String.class)
                        .block()).isNotNull());
    }

    @Test
    @DisplayName("resposta dentro do teto continua funcionando normalmente")
    void respostaRapidaNaoEhAfetada() {
        wireMock.stubFor(get(urlPathMatching("/activities/.*"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":123}")));

        String corpo = stravaWebClient.get()
                .uri("/activities/123")
                .retrieve()
                .bodyToMono(String.class)
                .block();

        assertThat(corpo).contains("123");
    }
}
