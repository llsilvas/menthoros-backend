package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.config.core.JacksonConfig;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.repository.TenantValidationRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import br.com.menthoros.backend.services.StravaOAuthService;
import br.com.menthoros.backend.services.UsuarioSyncService;
import br.com.menthoros.backend.testsupport.AuthWebMvcTestConfig;
import br.com.menthoros.backend.testsupport.JwtTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static br.com.menthoros.backend.testsupport.JwtTestSupport.atletaJwt;
import static br.com.menthoros.backend.testsupport.JwtTestSupport.tecnicoJwt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Autorização do StravaAuthController com a cadeia de segurança real — inclui a prova de que
 * o callback OAuth segue PÚBLICO (permitAll via CoreSecurityProperties.stravaPaths): o Strava
 * chama esse endpoint sem JWT.
 */
@WebMvcTest(StravaAuthController.class)
@Import({JacksonConfig.class, AuthWebMvcTestConfig.class})
class StravaAuthControllerAuthTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StravaOAuthService stravaOAuthService;

    @MockitoBean
    private TenantValidationRepository tenantValidationRepository;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private UsuarioSyncService usuarioSyncService;

    @MockitoBean
    private UsuarioRepository usuarioRepository;

    private static final UUID TENANT_ID = JwtTestSupport.TENANT_ID;
    private final UUID atletaId = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

    @BeforeEach
    void stubUsuarioAtivo() {
        JwtTestSupport.stubUsuarioAtivo(usuarioSyncService);
    }

    private void stubAtletaNoTenant() {
        when(tenantValidationRepository.resourceBelongsToTenant(atletaId, TENANT_ID)).thenReturn(true);
    }

    @Nested
    @DisplayName("callback — GET /api/v1/strava/callback (DEVE permanecer público)")
    class Callback {

        @Test
        @DisplayName("retorna 302 SEM autenticação — chamado pelos servidores do Strava")
        void retorna302SemAutenticacao() throws Exception {
            when(stravaOAuthService.findAtletaForCallback(atletaId)).thenReturn(new Atleta());

            mockMvc.perform(get("/api/v1/strava/callback")
                            .param("code", "codigo-oauth")
                            .param("state", atletaId.toString()))
                    .andExpect(status().isFound())
                    .andExpect(header().string(HttpHeaders.LOCATION,
                            org.hamcrest.Matchers.containsString("strava=success")));

            verify(stravaOAuthService).exchangeCodeForToken(anyString(), any(Atleta.class));
        }

        @Test
        @DisplayName("retorna 302 de erro sem autenticação quando Strava envia error")
        void retorna302DeErroSemAutenticacao() throws Exception {
            mockMvc.perform(get("/api/v1/strava/callback").param("error", "access_denied"))
                    .andExpect(status().isFound())
                    .andExpect(header().string(HttpHeaders.LOCATION,
                            org.hamcrest.Matchers.containsString("strava=error")));

            verifyNoInteractions(stravaOAuthService);
        }
    }

    @Nested
    @DisplayName("startAuth — GET /api/v1/strava/auth")
    class StartAuth {

        @Test
        @DisplayName("retorna 302 para TECNICO com atleta do tenant")
        void retorna302ParaTecnico() throws Exception {
            stubAtletaNoTenant();
            when(stravaOAuthService.getAuthorizationUrl(atletaId)).thenReturn("https://strava.test/oauth");

            mockMvc.perform(get("/api/v1/strava/auth")
                            .param("atletaId", atletaId.toString())
                            .with(tecnicoJwt()))
                    .andExpect(status().isFound());
        }

        @Test
        @DisplayName("retorna 403 para ATLETA")
        void retorna403ParaAtleta() throws Exception {
            mockMvc.perform(get("/api/v1/strava/auth")
                            .param("atletaId", atletaId.toString())
                            .with(atletaJwt()))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(stravaOAuthService);
        }
    }

    @Nested
    @DisplayName("getAuthorizationUrl — GET /api/v1/strava/auth/url/{atletaId}")
    class GetAuthorizationUrl {

        @Test
        @DisplayName("retorna 200 para TECNICO com atleta do tenant")
        void retorna200ParaTecnico() throws Exception {
            stubAtletaNoTenant();
            when(stravaOAuthService.getAuthorizationUrl(atletaId)).thenReturn("https://strava.test/oauth");

            mockMvc.perform(get("/api/v1/strava/auth/url/{atletaId}", atletaId).with(tecnicoJwt()))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("retorna 403 para ATLETA")
        void retorna403ParaAtleta() throws Exception {
            mockMvc.perform(get("/api/v1/strava/auth/url/{atletaId}", atletaId).with(atletaJwt()))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(stravaOAuthService);
        }

        @Test
        @DisplayName("retorna 403 quando atleta pertence a outro tenant")
        void retorna403CrossTenant() throws Exception {
            when(tenantValidationRepository.resourceBelongsToTenant(any(), any())).thenReturn(false);

            mockMvc.perform(get("/api/v1/strava/auth/url/{atletaId}", atletaId).with(tecnicoJwt()))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(stravaOAuthService);
        }

        @Test
        @DisplayName("retorna 401 quando requisição sem autenticação")
        void retorna401SemAutenticacao() throws Exception {
            mockMvc.perform(get("/api/v1/strava/auth/url/{atletaId}", atletaId))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("status e disconnect — endpoints por atletaId")
    class StatusEDisconnect {

        @Test
        @DisplayName("status retorna 403 para ATLETA")
        void statusRetorna403ParaAtleta() throws Exception {
            mockMvc.perform(get("/api/v1/strava/status/{atletaId}", atletaId).with(atletaJwt()))
                    .andExpect(status().isForbidden());

            verify(stravaOAuthService, never()).getStatus(any());
        }

        @Test
        @DisplayName("disconnect retorna 204 para TECNICO com atleta do tenant")
        void disconnectRetorna204ParaTecnico() throws Exception {
            stubAtletaNoTenant();

            mockMvc.perform(delete("/api/v1/strava/disconnect/{atletaId}", atletaId).with(tecnicoJwt()))
                    .andExpect(status().isNoContent());

            verify(stravaOAuthService).disconnect(atletaId);
        }

        @Test
        @DisplayName("disconnect retorna 403 para ATLETA")
        void disconnectRetorna403ParaAtleta() throws Exception {
            mockMvc.perform(delete("/api/v1/strava/disconnect/{atletaId}", atletaId).with(atletaJwt()))
                    .andExpect(status().isForbidden());

            verify(stravaOAuthService, never()).disconnect(any());
        }
    }
}
