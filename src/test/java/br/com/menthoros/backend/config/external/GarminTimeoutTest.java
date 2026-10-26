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

/** Mesmo formato de {@link StravaTimeoutTest}: bean real contra WireMock que atrasa 11 s. */
class GarminTimeoutTest {

    private WireMockServer wireMock;
    private WebClient garminWebClient;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();

        GarminProperties properties = new GarminProperties();
        properties.setApiBaseUrl(wireMock.baseUrl());

        garminWebClient = new GarminWebClientConfig(properties).garminWebClient();
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    @DisplayName("responseTimeout de 10s libera a thread antes de 11s")
    void responseTimeoutLiberaThread() {
        wireMock.stubFor(get(urlPathMatching("/partner-gateway/.*"))
                .willReturn(aResponse().withFixedDelay(11_000).withStatus(200)));

        assertTimeoutPreemptively(Duration.ofSeconds(11), () ->
                assertThatThrownBy(() -> garminWebClient.get()
                        .uri("/partner-gateway/rest/user/id")
                        .retrieve()
                        .bodyToMono(String.class)
                        .block()).isNotNull());
    }

    @Test
    @DisplayName("resposta dentro do teto continua funcionando")
    void respostaRapidaNaoEhAfetada() {
        wireMock.stubFor(get(urlPathMatching("/partner-gateway/.*"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"userId\":\"abc\"}")));

        String corpo = garminWebClient.get()
                .uri("/partner-gateway/rest/user/id")
                .retrieve()
                .bodyToMono(String.class)
                .block();

        assertThat(corpo).contains("abc");
    }
}
