package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.config.external.GarminProperties;
import br.com.menthoros.backend.config.external.GarminWebClientConfig;
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

import br.com.menthoros.backend.dto.garmin.GarminSchedule;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.netty.channel.ChannelOption;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.netty.http.client.HttpClient;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
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
    private GarminProperties props;
    private GarminClientImpl client;
    private ListAppender<ILoggingEvent> logCapture;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();

        props = new GarminProperties();
        props.setClientId("client-id");
        props.setClientSecret(SECRET);
        props.setRedirectUri("http://localhost/callback");
        props.setApiBaseUrl(wireMock.baseUrl());
        props.setTokenUri(wireMock.baseUrl() + "/di-oauth2-service/oauth/token");

        // O bean real: o teste do download depende do maxInMemorySize configurado nele.
        WebClient webClient = new GarminWebClientConfig(props).garminWebClient();
        client = new GarminClientImpl(webClient, props, URI.create(wireMock.baseUrl()));

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

    @Nested
    @DisplayName("Training API V2")
    class TrainingApi {

        private final ObjectMapper mapper = new ObjectMapper();

        @Test
        @DisplayName("criarWorkout: POST com Bearer e o JSON do treino, devolve o workoutId")
        void criaWorkout() throws Exception {
            JsonNode payload = mapper.readTree("{\"workoutName\":\"Longao\",\"sport\":\"RUNNING\"}");
            wireMock.stubFor(post(urlEqualTo("/workoutportal/workout/v2"))
                    .withHeader("Authorization", equalTo("Bearer " + TOKEN))
                    .withRequestBody(equalToJson("{\"workoutName\":\"Longao\",\"sport\":\"RUNNING\"}"))
                    .willReturn(okJson("{\"workoutId\":1000042,\"workoutName\":\"Longao\"}")));

            assertThat(client.criarWorkout(TOKEN, payload)).isEqualTo(1000042L);
        }

        @Test
        @DisplayName("criarWorkout: 200 sem workoutId e erro, nao id nulo")
        void criaWorkoutSemId() throws Exception {
            wireMock.stubFor(post(urlEqualTo("/workoutportal/workout/v2")).willReturn(okJson("{}")));

            assertThatThrownBy(() -> client.criarWorkout(TOKEN, mapper.readTree("{}")))
                    .isInstanceOf(GarminApiException.class)
                    .hasMessageContaining("workoutId");
        }

        @Test
        @DisplayName("atualizarWorkout: PUT /training-api/workout/v2/{id}")
        void atualizaWorkout() throws Exception {
            wireMock.stubFor(put(urlEqualTo("/training-api/workout/v2/1000042"))
                    .withHeader("Authorization", equalTo("Bearer " + TOKEN))
                    .willReturn(aResponse().withStatus(200)));

            assertThatCode(() -> client.atualizarWorkout(TOKEN, 1000042L, mapper.readTree("{\"sport\":\"RUNNING\"}")))
                    .doesNotThrowAnyException();
            wireMock.verify(1, com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor(
                    urlEqualTo("/training-api/workout/v2/1000042")));
        }

        @Test
        @DisplayName("deletarWorkout: DELETE /training-api/workout/v2/{id}")
        void deletaWorkout() {
            wireMock.stubFor(delete(urlEqualTo("/training-api/workout/v2/1000042"))
                    .willReturn(aResponse().withStatus(204)));

            assertThatCode(() -> client.deletarWorkout(TOKEN, 1000042L)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("buscarWorkout: GET devolve o JSON, com o workoutProvider")
        void buscaWorkout() {
            wireMock.stubFor(get(urlEqualTo("/training-api/workout/v2/1000042"))
                    .willReturn(okJson("{\"workoutId\":1000042,\"workoutProvider\":\"Menthoros\"}")));

            assertThat(client.buscarWorkout(TOKEN, 1000042L).get("workoutProvider").asText()).isEqualTo("Menthoros");
        }

        @Test
        @DisplayName("criarSchedule: POST com workoutId e date, devolve o scheduleId (número solto, como o Garmin responde)")
        void criaSchedule() {
            wireMock.stubFor(post(urlEqualTo("/training-api/schedule"))
                    .withHeader("Authorization", equalTo("Bearer " + TOKEN))
                    .withRequestBody(equalToJson("{\"workoutId\":1000042,\"date\":\"2026-11-02\"}"))
                    .willReturn(okJson("77")));

            assertThat(client.criarSchedule(TOKEN, 1000042L, LocalDate.of(2026, 11, 2))).isEqualTo(77L);
        }

        @Test
        @DisplayName("criarSchedule: também aceita o scheduleId dentro de um objeto")
        void criaScheduleComObjeto() {
            wireMock.stubFor(post(urlEqualTo("/training-api/schedule")).willReturn(okJson("{\"scheduleId\":77}")));

            assertThat(client.criarSchedule(TOKEN, 1000042L, LocalDate.of(2026, 11, 2))).isEqualTo(77L);
        }

        @Test
        @DisplayName("atualizarSchedule: PUT /training-api/schedule/{id} com workoutId e date")
        void atualizaSchedule() {
            wireMock.stubFor(put(urlEqualTo("/training-api/schedule/77"))
                    .withRequestBody(equalToJson("{\"workoutId\":1000042,\"date\":\"2026-11-03\"}"))
                    .willReturn(aResponse().withStatus(200)));

            assertThatCode(() -> client.atualizarSchedule(TOKEN, 77L, 1000042L, LocalDate.of(2026, 11, 3)))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("deletarSchedule: DELETE /training-api/schedule/{id}")
        void deletaSchedule() {
            wireMock.stubFor(delete(urlEqualTo("/training-api/schedule/77")).willReturn(aResponse().withStatus(204)));

            assertThatCode(() -> client.deletarSchedule(TOKEN, 77L)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("listarSchedules: GET com startDate e endDate, devolve scheduleId, workoutId e data")
        void listaSchedules() {
            wireMock.stubFor(get(urlPathEqualTo("/training-api/schedule"))
                    .withQueryParam("startDate", equalTo("2026-11-02"))
                    .withQueryParam("endDate", equalTo("2026-11-08"))
                    .willReturn(okJson("""
                            [{"scheduleId":77,"workoutId":1000042,"date":"2026-11-02"},
                             {"scheduleId":78,"workoutId":1000043,"date":"2026-11-04"}]
                            """)));

            List<GarminSchedule> schedules = client.listarSchedules(TOKEN, LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 8));

            assertThat(schedules).containsExactly(
                    new GarminSchedule(77L, 1000042L, LocalDate.of(2026, 11, 2)),
                    new GarminSchedule(78L, 1000043L, LocalDate.of(2026, 11, 4)));
        }

        @Test
        @DisplayName("listarSchedules: corpo vazio vira lista vazia")
        void listaSchedulesVazia() {
            wireMock.stubFor(get(urlPathEqualTo("/training-api/schedule")).willReturn(okJson("[]")));

            assertThat(client.listarSchedules(TOKEN, LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 8))).isEmpty();
        }

        @Test
        @DisplayName("412 (sem WORKOUT_IMPORT) e 429 (cota) chegam com o status, sem o token na mensagem")
        void errosCarregamStatus() throws Exception {
            wireMock.stubFor(post(urlEqualTo("/workoutportal/workout/v2")).willReturn(aResponse().withStatus(412).withBody(TOKEN)));
            wireMock.stubFor(post(urlEqualTo("/training-api/schedule")).willReturn(aResponse().withStatus(429)));

            assertThatThrownBy(() -> client.criarWorkout(TOKEN, mapper.readTree("{}")))
                    .isInstanceOfSatisfying(GarminApiException.class, e -> {
                        assertThat(e.getStatus().value()).isEqualTo(412);
                        assertThat(e.getMessage()).doesNotContain(TOKEN);
                    });
            assertThatThrownBy(() -> client.criarSchedule(TOKEN, 1L, LocalDate.of(2026, 11, 2)))
                    .isInstanceOfSatisfying(GarminApiException.class,
                            e -> assertThat(e.getStatus().value()).isEqualTo(429));
            assertThat(logCapture.list).noneMatch(ev -> ev.getFormattedMessage().contains(TOKEN));
        }

        @Test
        @DisplayName("deletar workout inexistente expõe o 404 para o chamador tratar como já apagado")
        void delete404() {
            wireMock.stubFor(delete(urlEqualTo("/training-api/workout/v2/9")).willReturn(aResponse().withStatus(404)));

            assertThatThrownBy(() -> client.deletarWorkout(TOKEN, 9L))
                    .isInstanceOfSatisfying(GarminApiException.class,
                            e -> assertThat(e.getStatus().value()).isEqualTo(404));
        }
    }

    @Nested
    @DisplayName("baixarArquivo")
    class BaixarArquivo {

        private static final String PATH = "/wellness-api/rest/activityFile";
        private static final String TOKEN_LINK = "token-do-link-secreto";

        private String callback() {
            return wireMock.baseUrl() + PATH + "?id=123&token=" + TOKEN_LINK;
        }

        @Test
        @DisplayName("baixa a fixture de 584 KB pelo garminWebClient real, sem estourar o buffer")
        void baixaFixture() throws Exception {
            byte[] fit;
            try (InputStream in = getClass().getResourceAsStream("/fit/corrida-15km-16laps.fit")) {
                fit = in.readAllBytes();
            }
            wireMock.stubFor(get(urlPathEqualTo(PATH))
                    .withQueryParam("token", equalTo(TOKEN_LINK))
                    .withHeader("Authorization", equalTo("Bearer access"))
                    .willReturn(aResponse().withStatus(200)
                            .withHeader("Content-Type", "application/octet-stream").withBody(fit)));

            assertThat(fit.length).isGreaterThan(256 * 1024);
            assertThat(client.baixarArquivo(callback(), "access")).isEqualTo(fit);
        }

        @ParameterizedTest
        @ValueSource(ints = {401, 403, 404, 410, 412, 429, 500, 503})
        @DisplayName("erro HTTP vira GarminApiException com o status, sem o token do link na mensagem nem no log")
        void erroComStatus(int status) {
            wireMock.stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(status).withBody(TOKEN_LINK)));

            assertThatThrownBy(() -> client.baixarArquivo(callback(), "access"))
                    .isInstanceOfSatisfying(GarminApiException.class, e -> {
                        assertThat(e.getStatus().value()).isEqualTo(status);
                        assertThat(e.getMessage()).doesNotContain(TOKEN_LINK);
                    });
            assertThat(logCapture.list).noneMatch(ev -> ev.getFormattedMessage().contains(TOKEN_LINK));
        }

        @Test
        @DisplayName("timeout vira GarminApiException com status nulo, sem o token do link na mensagem")
        void timeout() {
            wireMock.stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(200).withFixedDelay(2000)));
            HttpClient rapido = HttpClient.create()
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000)
                    .responseTimeout(Duration.ofMillis(200));
            GarminClientImpl comTimeoutCurto = new GarminClientImpl(
                    WebClient.builder().clientConnector(new ReactorClientHttpConnector(rapido)).build(),
                    props, URI.create(wireMock.baseUrl()));

            assertThatThrownBy(() -> comTimeoutCurto.baixarArquivo(callback(), "access"))
                    .isInstanceOfSatisfying(GarminApiException.class, e -> {
                        assertThat(e.getStatus()).isNull();
                        assertThat(e.getMessage()).doesNotContain(TOKEN_LINK);
                    });
        }

        @Test
        @DisplayName("200 sem corpo é erro, não arquivo vazio")
        void corpoVazio() {
            wireMock.stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(200)));

            assertThatThrownBy(() -> client.baixarArquivo(callback(), "access")).isInstanceOf(GarminApiException.class);
        }

        @Test
        @DisplayName("em produção só aceita https://apis.garmin.com/wellness-api/rest/activityFile")
        void origemDeProducao() {
            GarminClientImpl producao = new GarminClientImpl(WebClient.create(), props);

            assertThatThrownBy(() -> producao.baixarArquivo(callback(), "access"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("callbackURL inválido");
            assertThatThrownBy(() -> producao.baixarArquivo("http://apis.garmin.com" + PATH + "?token=x", "access"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "https://evil.example.com/wellness-api/rest/activityFile?token=x",
                "http://localhost:1/wellness-api/rest/activityFile?token=x",
                "{base}/wellness-api/rest/outra?token=x",
                "{base}/wellness-api/rest/activityFile/../../admin?token=x",
                "{baseUser}/wellness-api/rest/activityFile?token=x",
                "nao e url",
                ""})
        @DisplayName("URL fora da origem, do path ou malformada é rejeitada sem nenhuma requisição")
        void urlRejeitada(String modelo) {
            String url = modelo.replace("{baseUser}", wireMock.baseUrl().replace("://", "://user:pw@"))
                    .replace("{base}", wireMock.baseUrl());

            assertThatThrownBy(() -> client.baixarArquivo(url, "access"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("callbackURL inválido");
            assertThat(wireMock.getAllServeEvents()).isEmpty();
        }
    }
}
