package br.com.menthoros.backend.security;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** CA9: o segredo do webhook Garmin não vai ao log nem ao MDC. */
class StructuredLoggingFilterTest {

    private final StructuredLoggingFilter filter = new StructuredLoggingFilter();
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void setUp() {
        logs = new ListAppender<>();
        logs.start();
        ((Logger) LoggerFactory.getLogger(StructuredLoggingFilter.class)).addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(StructuredLoggingFilter.class)).detachAppender(logs);
    }

    private void executa(String uri) throws Exception {
        var request = new MockHttpServletRequest("POST", uri);
        request.setRequestURI(uri);
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    }

    @Test
    @DisplayName("webhook Garmin: o segredo vira *** nos dois logs e no MDC")
    void mascaraSegredo() throws Exception {
        executa("/api/v1/garmin/webhook/segredo-super-secreto");

        assertThat(logs.list).hasSize(2).allSatisfy(e -> {
            assertThat(e.getFormattedMessage()).contains("/api/v1/garmin/webhook/***")
                    .doesNotContain("segredo-super-secreto");
            assertThat(e.getMDCPropertyMap().get("request_uri")).isEqualTo("/api/v1/garmin/webhook/***");
        });
    }

    @Test
    @DisplayName("outras URIs não mudam")
    void outrasUrisIntactas() throws Exception {
        executa("/api/v1/integracoes/me/garmin");

        assertThat(logs.list).allSatisfy(e ->
                assertThat(e.getFormattedMessage()).contains("uri=/api/v1/integracoes/me/garmin"));
    }
}
