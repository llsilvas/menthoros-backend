package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.config.core.JacksonConfig;
import br.com.menthoros.backend.config.external.StravaProperties;
import br.com.menthoros.backend.repository.TenantValidationRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import br.com.menthoros.backend.services.StravaWebhookService;
import br.com.menthoros.backend.services.UsuarioSyncService;
import br.com.menthoros.backend.testsupport.AuthWebMvcTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prova que o webhook do Strava permanece PÚBLICO (permitAll via
 * CoreSecurityProperties.stravaPaths) com a cadeia de segurança real: o Strava chama GET/POST
 * sem JWT; a segurança é o verify token, não autenticação.
 */
@WebMvcTest(StravaWebhookController.class)
@Import({JacksonConfig.class, AuthWebMvcTestConfig.class})
class StravaWebhookControllerAuthTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StravaProperties stravaProperties;

    @MockitoBean
    private StravaWebhookService stravaWebhookService;

    @MockitoBean
    private TenantValidationRepository tenantValidationRepository;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private UsuarioSyncService usuarioSyncService;

    @MockitoBean
    private UsuarioRepository usuarioRepository;

    @BeforeEach
    void stubVerifyToken() {
        when(stravaProperties.getWebhookVerifyToken()).thenReturn("token-secreto");
    }

    @Test
    @DisplayName("GET de validação responde 200 SEM autenticação quando verify token confere")
    void getValidacaoPublicoComTokenCorreto() throws Exception {
        mockMvc.perform(get("/api/v1/strava/webhook")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "token-secreto")
                        .param("hub.challenge", "desafio-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.['hub.challenge']").value("desafio-123"));
    }

    @Test
    @DisplayName("GET de validação responde 403 quando verify token não confere")
    void getValidacao403ComTokenErrado() throws Exception {
        mockMvc.perform(get("/api/v1/strava/webhook")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "token-errado")
                        .param("hub.challenge", "desafio-123"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST de eventos responde 200 SEM autenticação")
    void postEventosPublico() throws Exception {
        mockMvc.perform(post("/api/v1/strava/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"object_type": "activity", "object_id": 123, "aspect_type": "create",
                                 "owner_id": 456, "subscription_id": 789, "event_time": 1720000000}
                                """))
                .andExpect(status().isOk());
    }
}
