package br.com.menthoros.backend.services.impl;

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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IntervalsIcuClientImplTest {

    private static final String API_KEY = "chave-super-secreta-nao-logavel";

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
    @DisplayName("validarApiKey")
    class ValidarApiKey {

        @Test
        @DisplayName("200 retorna o atleta autenticado (GET /athlete/0, Basic API_KEY:key)")
        void keyValidaRetornaAtleta() {
            wireMock.stubFor(get(urlEqualTo("/api/v1/athlete/0"))
                    .withBasicAuth("API_KEY", API_KEY)
                    .willReturn(okJson("{\"id\":\"i641775\",\"name\":\"Leandro Silva\"}")));

            Optional<IcuAthleteDto> atleta = client.validarApiKey(API_KEY);

            assertThat(atleta).isPresent();
            assertThat(atleta.get().id()).isEqualTo("i641775");
        }

        @Test
        @DisplayName("401 retorna vazio, sem exceção")
        void keyInvalidaRetornaVazio() {
            wireMock.stubFor(get(urlEqualTo("/api/v1/athlete/0"))
                    .willReturn(aResponse().withStatus(401)
                            .withBody("{\"status\":401,\"error\":\"Unauthorized\"}")));

            assertThat(client.validarApiKey(API_KEY)).isEmpty();
        }
    }

    @Nested
    @DisplayName("eventos")
    class Eventos {

        @Test
        @DisplayName("POST cria evento e devolve id + external_id (fixture do gate CA0)")
        void postCriaEvento() {
            wireMock.stubFor(post(urlEqualTo("/api/v1/athlete/i641775/events"))
                    .willReturn(okJson("{\"id\":122887509,\"external_id\":\"menthoros-abc\",\"name\":\"CONTINUO 15/07\",\"start_date_local\":\"2026-07-15T00:00:00\"}")));

            IcuEventDto evento = client.criarEvento(API_KEY, "i641775",
                    new ObjectMapper().createObjectNode().put("category", "WORKOUT"));

            assertThat(evento.id()).isEqualTo(122887509L);
            assertThat(evento.externalId()).isEqualTo("menthoros-abc");
        }

        @Test
        @DisplayName("PUT 404 lança IntervalsIcuApiException com status NOT_FOUND")
        void put404LancaExcecaoTipada() {
            wireMock.stubFor(put(urlEqualTo("/api/v1/athlete/i641775/events/999"))
                    .willReturn(aResponse().withStatus(404)));

            assertThatThrownBy(() -> client.atualizarEvento(API_KEY, "i641775", 999L,
                    new ObjectMapper().createObjectNode()))
                    .isInstanceOf(IntervalsIcuApiException.class)
                    .satisfies(e -> assertThat(((IntervalsIcuApiException) e).getStatus().value()).isEqualTo(404));
        }

        @Test
        @DisplayName("GET lista eventos da janela por oldest/newest")
        void getListaEventos() {
            wireMock.stubFor(get(urlPathEqualTo("/api/v1/athlete/i641775/events"))
                    .withBasicAuth("API_KEY", API_KEY)
                    .withQueryParam("oldest", equalTo("2026-07-14"))
                    .withQueryParam("newest", equalTo("2026-07-20"))
                    .willReturn(okJson("[{\"id\":1,\"external_id\":\"menthoros-x\",\"name\":\"A\",\"start_date_local\":\"2026-07-15T00:00:00\"}]")));

            List<IcuEventDto> eventos = client.listarEventos(API_KEY, "i641775",
                    LocalDate.of(2026, 7, 14), LocalDate.of(2026, 7, 20));

            assertThat(eventos).hasSize(1);
            assertThat(eventos.get(0).externalId()).isEqualTo("menthoros-x");
        }
    }

    @Test
    @DisplayName("a API key nunca aparece em log nem na mensagem de exceção")
    void keyNuncaVaza() {
        wireMock.stubFor(post(urlEqualTo("/api/v1/athlete/i641775/events"))
                .willReturn(aResponse().withStatus(500).withBody("boom")));

        assertThatThrownBy(() -> client.criarEvento(API_KEY, "i641775",
                new ObjectMapper().createObjectNode()))
                .isInstanceOf(IntervalsIcuApiException.class)
                .satisfies(e -> {
                    assertThat(e.getMessage()).doesNotContain(API_KEY);
                    assertThat(String.valueOf(e.getCause())).doesNotContain(API_KEY);
                });

        String logs = logCapture.list.stream().map(ILoggingEvent::getFormattedMessage)
                .reduce("", (a, b) -> a + "\n" + b);
        assertThat(logs).doesNotContain(API_KEY);
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
            client.validarApiKey(API_KEY);

            // snapshot via toArray: os event loops do netty seguem logando em background
            // e iterar a lista viva do ListAppender lançaria ConcurrentModificationException
            String logs = java.util.Arrays.stream(logCapture.list.toArray(new ILoggingEvent[0]))
                    .map(ILoggingEvent::getFormattedMessage)
                    .reduce("", (a, b) -> a + "\n" + b);
            assertThat(logs).doesNotContain("Authorization").doesNotContain(API_KEY);
        } finally {
            for (int i = 0; i < loggers.size(); i++) {
                loggers.get(i).detachAppender(logCapture);
                loggers.get(i).setLevel(originais.get(i));
            }
        }
    }
}
