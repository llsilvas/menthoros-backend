package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.config.core.JacksonConfig;
import br.com.menthoros.backend.config.external.GarminProperties;
import br.com.menthoros.backend.repository.TenantValidationRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import br.com.menthoros.backend.services.GarminWebhookService;
import br.com.menthoros.backend.services.UsuarioSyncService;
import br.com.menthoros.backend.testsupport.AuthWebMvcTestConfig;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CA7/CA8/CA9: webhook público, segredo no caminho, 404 sem revelar o endpoint, corpo fora do log. */
@WebMvcTest(GarminWebhookController.class)
@Import({JacksonConfig.class, AuthWebMvcTestConfig.class})
class GarminWebhookControllerTest {

    private static final String SEGREDO = "segredo-do-webhook-123";
    private static final String CORPO = "{\"deregistrations\":[{\"userId\":\"u-sensivel-999\"}]}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GarminProperties properties;
    @MockitoBean
    private GarminWebhookService webhookService;
    @MockitoBean
    private TenantValidationRepository tenantValidationRepository;
    @MockitoBean
    private JwtDecoder jwtDecoder;
    @MockitoBean
    private UsuarioSyncService usuarioSyncService;
    @MockitoBean
    private UsuarioRepository usuarioRepository;

    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void setUp() {
        when(properties.getWebhookSecret()).thenReturn(SEGREDO);
        logs = new ListAppender<>();
        logs.start();
        ((Logger) LoggerFactory.getLogger("br.com.menthoros.backend")).addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger("br.com.menthoros.backend")).detachAppender(logs);
    }

    @Test
    @DisplayName("segredo certo, sem JWT: 200 e processa o corpo")
    void segredoCerto() throws Exception {
        mockMvc.perform(post("/api/v1/garmin/webhook/" + SEGREDO)
                        .contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(status().isOk());

        verify(webhookService).processar(any());
    }

    @Test
    @DisplayName("segredo errado: 404 e nada é processado")
    void segredoErrado() throws Exception {
        mockMvc.perform(post("/api/v1/garmin/webhook/errado")
                        .contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(status().isNotFound());

        verifyNoInteractions(webhookService);
    }

    @Test
    @DisplayName("segredo não configurado: 404 mesmo com caminho qualquer")
    void naoConfigurado() throws Exception {
        when(properties.getWebhookSecret()).thenReturn("");

        mockMvc.perform(post("/api/v1/garmin/webhook/qualquer")
                        .contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(status().isNotFound());

        verifyNoInteractions(webhookService);
    }

    @Test
    @DisplayName("corpo vazio com segredo certo: 200")
    void corpoVazio() throws Exception {
        mockMvc.perform(post("/api/v1/garmin/webhook/" + SEGREDO)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("o corpo e o segredo não vão ao log do controller")
    void semCorpoNoLog() throws Exception {
        mockMvc.perform(post("/api/v1/garmin/webhook/" + SEGREDO)
                .contentType(MediaType.APPLICATION_JSON).content(CORPO));
        mockMvc.perform(post("/api/v1/garmin/webhook/errado")
                .contentType(MediaType.APPLICATION_JSON).content(CORPO));

        assertThat(logs.list).noneMatch(e -> e.getFormattedMessage().contains("u-sensivel-999")
                || e.getFormattedMessage().contains(SEGREDO));
    }
}
