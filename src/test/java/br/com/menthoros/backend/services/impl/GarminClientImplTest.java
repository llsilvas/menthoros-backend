package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.config.external.GarminProperties;
import br.com.menthoros.backend.dto.garmin.GarminTokenResponse;
import br.com.menthoros.backend.exception.GarminApiException;
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
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GarminClientImplTest {

    private static final String TOKEN = "access-token-secreto-nao-logavel";
    private static final String REFRESH = "refresh-token-secreto-nao-logavel";
    private static final String CODE = "code-secreto";
    private static final String VERIFIER = "verifier-secreto";
    private static final String SECRET = "client-secret-secreto";

    private WireMockServer wireMock;
    private GarminClientImpl client;
    private ListAppender<ILoggingEvent> logCapture;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();

        GarminProperties props = new GarminProperties();
        props.setClientId("client-id");
        props.setClientSecret(SECRET);
        props.setRedirectUri("http://localhost/callback");
        props.setApiBaseUrl(wireMock.baseUrl());
        props.setTokenUri(wireMock.baseUrl() + "/di-oauth2-service/oauth/token");

        WebClient webClient = WebClient.builder().baseUrl(wireMock.baseUrl()).build();
        client = new GarminClientImpl(webClient, props);

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
    @DisplayName("trocarCode")
    class TrocarCode {

        @Test
        @DisplayName("envia form PKCE e lê expires_in e refresh_token_expires_in")
        void trocaCodeComPkce() {
            wireMock.stubFor(post(urlEqualTo("/di-oauth2-service/oauth/token"))
                    .withHeader("Content-Type", containing("application/x-www-form-urlencoded"))
                    .withRequestBody(containing("grant_type=authorization_code"))
                    .withRequestBody(containing("code=" + CODE))
                    .withRequestBody(containing("code_verifier=" + VERIFIER))
                    .withRequestBody(containing("client_secret=" + SECRET))
                    .withRequestBody(containing("redirect_uri="))
                    .willReturn(okJson("""
                            {"access_token":"%s","refresh_token":"%s","token_type":"Bearer",
                             "expires_in":86400,"refresh_token_expires_in":7775999,"scope":"PARTNER_READ"}
                            """.formatted(TOKEN, REFRESH))));

            GarminTokenResponse resposta = client.trocarCode(CODE, VERIFIER);

            assertThat(resposta.accessToken()).isEqualTo(TOKEN);
            assertThat(resposta.refreshToken()).isEqualTo(REFRESH);
            assertThat(resposta.expiresIn()).isEqualTo(86400);
            assertThat(resposta.refreshTokenExpiresInOuPadrao()).isEqualTo(7775999);
        }

        @Test
        @DisplayName("sem refresh_token_expires_in assume 90 dias")
        void semRefreshExpiresAssume90Dias() {
            wireMock.stubFor(post(urlEqualTo("/di-oauth2-service/oauth/token"))
                    .willReturn(okJson("""
                            {"access_token":"%s","refresh_token":"%s","expires_in":86400}
                            """.formatted(TOKEN, REFRESH))));

            GarminTokenResponse resposta = client.trocarCode(CODE, VERIFIER);

            assertThat(resposta.refreshTokenExpiresInOuPadrao()).isEqualTo(Duration.ofDays(90).toSeconds());
        }

        @Test
        @DisplayName("400 vira GarminApiException com status, sem code, verifier ou secret na mensagem")
        void erro400SemSegredos() {
            wireMock.stubFor(post(urlEqualTo("/di-oauth2-service/oauth/token"))
                    .willReturn(aResponse().withStatus(400)
                            .withBody("{\"error\":\"invalid_grant\",\"echo\":\"" + CODE + "\"}")));

            assertThatThrownBy(() -> client.trocarCode(CODE, VERIFIER))
                    .isInstanceOfSatisfying(GarminApiException.class, e -> {
                        assertThat(e.getStatus().value()).isEqualTo(400);
                        assertThat(e.getMessage()).doesNotContain(CODE, VERIFIER, SECRET);
                    });
            assertThat(logCapture.list).noneMatch(ev ->
                    ev.getFormattedMessage().contains(CODE) || ev.getFormattedMessage().contains(SECRET));
        }
    }

    @Nested
    @DisplayName("renovar")
    class Renovar {

        @Test
        @DisplayName("envia grant_type=refresh_token e devolve o par novo")
        void renovaToken() {
            wireMock.stubFor(post(urlEqualTo("/di-oauth2-service/oauth/token"))
                    .withRequestBody(containing("grant_type=refresh_token"))
                    .withRequestBody(containing("refresh_token=" + REFRESH))
                    .willReturn(okJson("""
                            {"access_token":"novo-access","refresh_token":"novo-refresh",
                             "expires_in":86400,"refresh_token_expires_in":7775999}
                            """)));

            GarminTokenResponse resposta = client.renovar(REFRESH);

            assertThat(resposta.accessToken()).isEqualTo("novo-access");
            assertThat(resposta.refreshToken()).isEqualTo("novo-refresh");
        }

        @Test
        @DisplayName("401 vira GarminApiException(401) sem o refresh token na mensagem")
        void erro401() {
            wireMock.stubFor(post(urlEqualTo("/di-oauth2-service/oauth/token"))
                    .willReturn(aResponse().withStatus(401)));

            assertThatThrownBy(() -> client.renovar(REFRESH))
                    .isInstanceOfSatisfying(GarminApiException.class, e -> {
                        assertThat(e.getStatus().value()).isEqualTo(401);
                        assertThat(e.getMessage()).doesNotContain(REFRESH);
                    });
        }

        @Test
        @DisplayName("200 sem access_token é erro, não resposta válida")
        void semAccessTokenEhErro() {
            wireMock.stubFor(post(urlEqualTo("/di-oauth2-service/oauth/token"))
                    .willReturn(okJson("{}")));

            assertThatThrownBy(() -> client.renovar(REFRESH))
                    .isInstanceOf(GarminApiException.class)
                    .hasMessageContaining("access_token");
        }
    }

    @Nested
    @DisplayName("userId")
    class UserId {

        @Test
        @DisplayName("GET /partner-gateway/rest/user/id com Bearer devolve userId")
        void devolveUserId() {
            wireMock.stubFor(get(urlEqualTo("/partner-gateway/rest/user/id"))
                    .withHeader("Authorization", equalTo("Bearer " + TOKEN))
                    .willReturn(okJson("{\"userId\":\"d3315b1072421d0dd7c8f6b8e1de4df8\"}")));

            assertThat(client.userId(TOKEN)).isEqualTo("d3315b1072421d0dd7c8f6b8e1de4df8");
        }

        @Test
        @DisplayName("500 vira GarminApiException(500) sem o token na mensagem nem no log")
        void erro500SemToken() {
            wireMock.stubFor(get(urlEqualTo("/partner-gateway/rest/user/id"))
                    .willReturn(aResponse().withStatus(500).withBody(TOKEN)));

            assertThatThrownBy(() -> client.userId(TOKEN))
                    .isInstanceOfSatisfying(GarminApiException.class, e -> {
                        assertThat(e.getStatus().value()).isEqualTo(500);
                        assertThat(e.getMessage()).doesNotContain(TOKEN);
                    });
            assertThat(logCapture.list).noneMatch(ev -> ev.getFormattedMessage().contains(TOKEN));
        }

        @Test
        @DisplayName("falha de transporte vira GarminApiException com status nulo")
        void transporte() {
            wireMock.stop();

            assertThatThrownBy(() -> client.userId(TOKEN))
                    .isInstanceOfSatisfying(GarminApiException.class, e -> assertThat(e.getStatus()).isNull());
        }
    }

    @Nested
    @DisplayName("permissoes")
    class Permissoes {

        @Test
        @DisplayName("lê a lista permissions")
        void lePermissoes() {
            wireMock.stubFor(get(urlEqualTo("/partner-gateway/rest/user/permissions"))
                    .withHeader("Authorization", equalTo("Bearer " + TOKEN))
                    .willReturn(okJson("{\"permissions\":[\"ACTIVITY_EXPORT\",\"WORKOUT_IMPORT\"]}")));

            assertThat(client.permissoes(TOKEN)).containsExactly("ACTIVITY_EXPORT", "WORKOUT_IMPORT");
        }

        @Test
        @DisplayName("corpo sem permissions devolve lista vazia")
        void semPermissions() {
            wireMock.stubFor(get(urlEqualTo("/partner-gateway/rest/user/permissions"))
                    .willReturn(okJson("{}")));

            assertThat(client.permissoes(TOKEN)).isEmpty();
        }
    }

    @Nested
    @DisplayName("removerRegistro")
    class RemoverRegistro {

        @Test
        @DisplayName("DELETE 204 conclui sem exceção")
        void remove204() {
            wireMock.stubFor(delete(urlEqualTo("/partner-gateway/rest/user/registration"))
                    .withHeader("Authorization", equalTo("Bearer " + TOKEN))
                    .willReturn(aResponse().withStatus(204)));

            assertThatCode(() -> client.removerRegistro(TOKEN)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("500 lança GarminApiException — o best-effort é decisão do service")
        void erro500Lanca() {
            wireMock.stubFor(delete(urlEqualTo("/partner-gateway/rest/user/registration"))
                    .willReturn(aResponse().withStatus(500)));

            assertThatThrownBy(() -> client.removerRegistro(TOKEN))
                    .isInstanceOfSatisfying(GarminApiException.class,
                            e -> assertThat(e.getStatus().value()).isEqualTo(500));
        }
    }
}
