package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.intervalsicu.IcuActivityDto;
import br.com.menthoros.backend.dto.intervalsicu.IcuAthleteDto;
import br.com.menthoros.backend.dto.intervalsicu.IcuEventDto;
import br.com.menthoros.backend.exception.IntervalsIcuApiException;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IntervalsIcuClientImplTest {

    private static final String TOKEN = "token-oauth-super-secreto-nao-logavel";

    private WireMockServer wireMock;
    private IntervalsIcuClientImpl client;
    private ListAppender<ILoggingEvent> logCapture;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
        WebClient webClient = WebClient.builder().baseUrl(wireMock.baseUrl()).build();
        client = new IntervalsIcuClientImpl(webClient);

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
    @DisplayName("validarToken")
    class ValidarToken {

        @Test
        @DisplayName("200 retorna o atleta autenticado (GET /athlete/0, Bearer)")
        void keyValidaRetornaAtleta() {
            wireMock.stubFor(get(urlEqualTo("/api/v1/athlete/0"))
                    .withHeader("Authorization", equalTo("Bearer " + TOKEN))
                    .willReturn(okJson("{\"id\":\"i641775\",\"name\":\"Leandro Silva\"}")));

            Optional<IcuAthleteDto> atleta = client.validarToken(TOKEN);

            assertThat(atleta).isPresent();
            assertThat(atleta.get().id()).isEqualTo("i641775");
        }

        @Test
        @DisplayName("401 retorna vazio, sem exceção")
        void keyInvalidaRetornaVazio() {
            wireMock.stubFor(get(urlEqualTo("/api/v1/athlete/0"))
                    .willReturn(aResponse().withStatus(401)
                            .withBody("{\"status\":401,\"error\":\"Unauthorized\"}")));

            assertThat(client.validarToken(TOKEN)).isEmpty();
        }
    }

    @Nested
    @DisplayName("eventos")
    class Eventos {

        @Test
        @DisplayName("POST cria evento e devolve id + external_id (fixture do gate CA0)")
        void postCriaEvento() {
            wireMock.stubFor(post(urlEqualTo("/api/v1/athlete/i641775/events"))
                    .withHeader("Authorization", equalTo("Bearer " + TOKEN))
                    .willReturn(okJson("{\"id\":122887509,\"external_id\":\"menthoros-abc\",\"name\":\"CONTINUO 15/07\",\"start_date_local\":\"2026-07-15T00:00:00\"}")));

            IcuEventDto evento = client.criarEvento(TOKEN, "i641775",
                    new ObjectMapper().createObjectNode().put("category", "WORKOUT"));

            assertThat(evento.id()).isEqualTo(122887509L);
            assertThat(evento.externalId()).isEqualTo("menthoros-abc");
        }

        // CA7: o push de treino planejado ao relógio é o canal que já roda em produção desde
        // 2026-07-14. Um Basic remanescente aqui só apareceria na primeira aprovação de plano
        // depois do deploy — longe da causa. Por isso a asserção é sobre o header, explícita.
        @Test
        @DisplayName("PUT atualiza evento com Bearer")
        void putAtualizaEventoComBearer() {
            wireMock.stubFor(put(urlEqualTo("/api/v1/athlete/i641775/events/42"))
                    .withHeader("Authorization", equalTo("Bearer " + TOKEN))
                    .willReturn(okJson("{\"id\":42,\"external_id\":\"menthoros-y\",\"name\":\"B\",\"start_date_local\":\"2026-07-16T00:00:00\"}")));

            IcuEventDto evento = client.atualizarEvento(TOKEN, "i641775", 42L,
                    new ObjectMapper().createObjectNode().put("category", "WORKOUT"));

            assertThat(evento.id()).isEqualTo(42L);
        }

        @Test
        @DisplayName("DELETE remove evento com Bearer")
        void deleteRemoveEventoComBearer() {
            wireMock.stubFor(delete(urlEqualTo("/api/v1/athlete/i641775/events/42"))
                    .withHeader("Authorization", equalTo("Bearer " + TOKEN))
                    .willReturn(aResponse().withStatus(204)));

            client.deletarEvento(TOKEN, "i641775", 42L);

            wireMock.verify(deleteRequestedFor(urlEqualTo("/api/v1/athlete/i641775/events/42"))
                    .withHeader("Authorization", equalTo("Bearer " + TOKEN)));
        }

        @Test
        @DisplayName("PUT 404 lança IntervalsIcuApiException com status NOT_FOUND")
        void put404LancaExcecaoTipada() {
            wireMock.stubFor(put(urlEqualTo("/api/v1/athlete/i641775/events/999"))
                    .willReturn(aResponse().withStatus(404)));

            assertThatThrownBy(() -> client.atualizarEvento(TOKEN, "i641775", 999L,
                    new ObjectMapper().createObjectNode()))
                    .isInstanceOf(IntervalsIcuApiException.class)
                    .satisfies(e -> assertThat(((IntervalsIcuApiException) e).getStatus().value()).isEqualTo(404));
        }

        @Test
        @DisplayName("GET lista eventos da janela por oldest/newest")
        void getListaEventos() {
            wireMock.stubFor(get(urlPathEqualTo("/api/v1/athlete/i641775/events"))
                    .withHeader("Authorization", equalTo("Bearer " + TOKEN))
                    .withQueryParam("oldest", equalTo("2026-07-14"))
                    .withQueryParam("newest", equalTo("2026-07-20"))
                    .willReturn(okJson("[{\"id\":1,\"external_id\":\"menthoros-x\",\"name\":\"A\",\"start_date_local\":\"2026-07-15T00:00:00\"}]")));

            List<IcuEventDto> eventos = client.listarEventos(TOKEN, "i641775",
                    LocalDate.of(2026, 7, 14), LocalDate.of(2026, 7, 20));

            assertThat(eventos).hasSize(1);
            assertThat(eventos.get(0).externalId()).isEqualTo("menthoros-x");
        }
    }

    @Nested
    @DisplayName("buscarAtividade")
    class BuscarAtividade {

        @Test
        @DisplayName("200 retorna a activity desserializada (GET /activity/{id}, Bearer)")
        void sucessoRetornaActivity() {
            wireMock.stubFor(get(urlEqualTo("/api/v1/activity/i86400275"))
                    .withHeader("Authorization", equalTo("Bearer " + TOKEN))
                    .willReturn(okJson("""
                            {"id":"i86400275","icu_athlete_id":"i641775","type":"Run",
                             "start_date_local":"2026-07-16T06:30:00","moving_time":1800,
                             "distance":5000.0}
                            """)));

            IcuActivityDto activity = client.buscarAtividade(TOKEN, "i86400275", false);

            assertThat(activity.id()).isEqualTo("i86400275");
            assertThat(activity.athleteId()).isEqualTo("i641775");
            assertThat(activity.movingTimeSeg()).isEqualTo(1800);
        }

        @Test
        @DisplayName("comIntervalos=true acrescenta ?intervals=true a URI")
        void comIntervalosAcrescentaQueryParam() {
            wireMock.stubFor(get(urlEqualTo("/api/v1/activity/i171415754?intervals=true"))
                    .withHeader("Authorization", equalTo("Bearer " + TOKEN))
                    .willReturn(okJson("""
                            {"id":"i171415754","icu_athlete_id":"i641775","type":"Run",
                             "icu_lap_count":2,
                             "icu_intervals":[
                               {"id":7130765,"type":"WORK","distance":1001.92,"moving_time":388,
                                "average_speed":2.582268,"average_cadence":81.3866,
                                "average_vertical_oscillation":113.24149,"zone":1,
                                "intensity":75,"average_gradient":0.0011977126},
                               {"id":1483778,"type":"RECOVERY","distance":500.67,"moving_time":195}
                             ]}
                            """)));

            IcuActivityDto activity = client.buscarAtividade(TOKEN, "i171415754", true);

            assertThat(activity.lapCount()).isEqualTo(2);
            assertThat(activity.intervalos()).hasSize(2);
            assertThat(activity.intervalos().get(0).type()).isEqualTo("WORK");
            assertThat(activity.intervalos().get(0).zone()).isEqualTo(1);
            assertThat(activity.intervalos().get(0).averageGradient()).isEqualTo(0.0011977126);
        }

        @Test
        @DisplayName("comIntervalos=false nao acrescenta o query param e a lista vem nula")
        void semIntervalosNaoAcrescentaQueryParam() {
            wireMock.stubFor(get(urlEqualTo("/api/v1/activity/i171415754"))
                    .willReturn(okJson("""
                            {"id":"i171415754","icu_athlete_id":"i641775","type":"Run"}
                            """)));

            IcuActivityDto activity = client.buscarAtividade(TOKEN, "i171415754", false);

            assertThat(activity.intervalos()).isNull();
            // urlEqualTo e exato: um ?intervals=... enviado por engano nao casaria com o stub.
            wireMock.verify(getRequestedFor(urlEqualTo("/api/v1/activity/i171415754")));
        }

        @Test
        @DisplayName("activity sem intervalos no corpo desserializa com lista nula, sem NPE")
        void corpoSemIntervalosNaoQuebra() {
            wireMock.stubFor(get(urlEqualTo("/api/v1/activity/i2?intervals=true"))
                    .willReturn(okJson("""
                            {"id":"i2","icu_athlete_id":"i641775","type":"Run","moving_time":600}
                            """)));

            IcuActivityDto activity = client.buscarAtividade(TOKEN, "i2", true);

            assertThat(activity.intervalos()).isNull();
            assertThat(activity.lapCount()).isNull();
        }

        @Test
        @DisplayName("404 lança IntervalsIcuApiException com status 404")
        void notFoundLancaExcecaoComStatus() {
            wireMock.stubFor(get(urlEqualTo("/api/v1/activity/inexistente?intervals=true"))
                    .willReturn(aResponse().withStatus(404)));

            assertThatThrownBy(() -> client.buscarAtividade(TOKEN, "inexistente", true))
                    .isInstanceOf(IntervalsIcuApiException.class)
                    .satisfies(e -> assertThat(((IntervalsIcuApiException) e).getStatus().value()).isEqualTo(404));
        }

        @Test
        @DisplayName("403 lança IntervalsIcuApiException com status 403 (activity de outro atleta)")
        void forbiddenLancaExcecaoComStatus() {
            wireMock.stubFor(get(urlEqualTo("/api/v1/activity/i999?intervals=true"))
                    .willReturn(aResponse().withStatus(403)));

            assertThatThrownBy(() -> client.buscarAtividade(TOKEN, "i999", true))
                    .isInstanceOf(IntervalsIcuApiException.class)
                    .satisfies(e -> assertThat(((IntervalsIcuApiException) e).getStatus().value()).isEqualTo(403));
        }

        @Test
        @DisplayName("falha de transporte lança IntervalsIcuApiException sem status HTTP")
        void falhaDeTransporteLancaExcecao() {
            wireMock.stop();

            assertThatThrownBy(() -> client.buscarAtividade(TOKEN, "i1", true))
                    .isInstanceOf(IntervalsIcuApiException.class)
                    .satisfies(e -> assertThat(((IntervalsIcuApiException) e).getStatus()).isNull());
        }
    }

    @Nested
    @DisplayName("revogarAcesso")
    class RevogarAcesso {

        @Test
        @DisplayName("DELETE /disconnect-app com Bearer do próprio atleta")
        void revogaComBearer() {
            wireMock.stubFor(delete(urlEqualTo("/api/v1/disconnect-app"))
                    .withHeader("Authorization", equalTo("Bearer " + TOKEN))
                    .willReturn(aResponse().withStatus(200)));

            client.revogarAcesso(TOKEN);

            wireMock.verify(deleteRequestedFor(urlEqualTo("/api/v1/disconnect-app"))
                    .withHeader("Authorization", equalTo("Bearer " + TOKEN)));
        }

        // D7: a intenção do atleta é sair. Se o provedor estiver fora do ar, travar a
        // desconexão local deixaria o Menthoros tentando usar um token que o atleta já
        // quis descartar — pior dos dois mundos.
        @Test
        @DisplayName("erro do provedor não propaga (best-effort)")
        void erroNaoPropaga() {
            wireMock.stubFor(delete(urlEqualTo("/api/v1/disconnect-app"))
                    .willReturn(aResponse().withStatus(500).withBody("boom")));

            assertThatCode(() -> client.revogarAcesso(TOKEN)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("token inválido (401) não propaga")
        void token401NaoPropaga() {
            wireMock.stubFor(delete(urlEqualTo("/api/v1/disconnect-app"))
                    .willReturn(aResponse().withStatus(401)));

            assertThatCode(() -> client.revogarAcesso(TOKEN)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("o token não vaza no log da falha de revogação")
        void tokenNaoVazaNaFalha() {
            wireMock.stubFor(delete(urlEqualTo("/api/v1/disconnect-app"))
                    .willReturn(aResponse().withStatus(500).withBody("boom")));

            client.revogarAcesso(TOKEN);

            String logs = java.util.Arrays.stream(logCapture.list.toArray(new ILoggingEvent[0]))
                    .map(ILoggingEvent::getFormattedMessage)
                    .reduce("", (a, b) -> a + "\n" + b);
            assertThat(logs).doesNotContain(TOKEN);
        }
    }

    @Test
    @DisplayName("a API key nunca aparece em log nem na mensagem de exceção")
    void keyNuncaVaza() {
        wireMock.stubFor(post(urlEqualTo("/api/v1/athlete/i641775/events"))
                .willReturn(aResponse().withStatus(500).withBody("boom")));

        assertThatThrownBy(() -> client.criarEvento(TOKEN, "i641775",
                new ObjectMapper().createObjectNode()))
                .isInstanceOf(IntervalsIcuApiException.class)
                .satisfies(e -> {
                    assertThat(e.getMessage()).doesNotContain(TOKEN);
                    assertThat(String.valueOf(e.getCause())).doesNotContain(TOKEN);
                });

        String logs = logCapture.list.stream().map(ILoggingEvent::getFormattedMessage)
                .reduce("", (a, b) -> a + "\n" + b);
        assertThat(logs).doesNotContain(TOKEN);
    }

    @Test
    @DisplayName("nível DEBUG não expõe o header Authorization (client e reactor.netty)")
    void debugNaoExpoeAuthorization() {
        // eleva também os loggers do HTTP client: é neles que um wiretap vazaria o header
        List<Logger> loggers = List.of(
                (Logger) LoggerFactory.getLogger(IntervalsIcuClientImpl.class),
                (Logger) LoggerFactory.getLogger("reactor.netty.http.client"),
                (Logger) LoggerFactory.getLogger("reactor.netty"));
        List<ch.qos.logback.classic.Level> originais = loggers.stream().map(Logger::getLevel).toList();
        loggers.forEach(l -> {
            l.setLevel(ch.qos.logback.classic.Level.DEBUG);
            l.addAppender(logCapture);
        });
        try {
            wireMock.stubFor(get(urlEqualTo("/api/v1/athlete/0")).willReturn(okJson("{\"id\":\"i1\",\"name\":\"x\"}")));
            client.validarToken(TOKEN);

            // snapshot via toArray: os event loops do netty seguem logando em background
            // e iterar a lista viva do ListAppender lançaria ConcurrentModificationException
            String logs = java.util.Arrays.stream(logCapture.list.toArray(new ILoggingEvent[0]))
                    .map(ILoggingEvent::getFormattedMessage)
                    .reduce("", (a, b) -> a + "\n" + b);
            assertThat(logs).doesNotContain("Authorization").doesNotContain(TOKEN);
        } finally {
            for (int i = 0; i < loggers.size(); i++) {
                loggers.get(i).detachAppender(logCapture);
                loggers.get(i).setLevel(originais.get(i));
            }
        }
    }
}
