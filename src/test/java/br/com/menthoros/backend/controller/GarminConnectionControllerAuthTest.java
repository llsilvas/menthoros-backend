package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.config.core.JacksonConfig;
import br.com.menthoros.backend.dto.output.GarminConnectionStatusDto;
import br.com.menthoros.backend.exception.GarminCanalIndisponivelException;
import br.com.menthoros.backend.repository.TenantValidationRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import br.com.menthoros.backend.services.AtletaProgressService;
import br.com.menthoros.backend.services.GarminConnectionService;
import br.com.menthoros.backend.services.GarminOAuthService;
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
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static br.com.menthoros.backend.testsupport.JwtTestSupport.adminJwt;
import static br.com.menthoros.backend.testsupport.JwtTestSupport.atletaJwt;
import static br.com.menthoros.backend.testsupport.JwtTestSupport.tecnicoJwt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Autorização do GarminConnectionController com a cadeia de segurança real (3.1 e 3.3). */
@WebMvcTest(GarminConnectionController.class)
@Import({JacksonConfig.class, AuthWebMvcTestConfig.class})
class GarminConnectionControllerAuthTest {

    private static final String BASE = "/api/v1/integracoes/me/garmin";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GarminConnectionService connectionService;
    @MockitoBean
    private GarminOAuthService oauthService;
    @MockitoBean
    private AtletaProgressService atletaProgressService;
    @MockitoBean
    private TenantValidationRepository tenantValidationRepository;
    @MockitoBean
    private JwtDecoder jwtDecoder;
    @MockitoBean
    private UsuarioSyncService usuarioSyncService;
    @MockitoBean
    private UsuarioRepository usuarioRepository;

    private final UUID atletaId = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

    @BeforeEach
    void stubUsuarioAtivo() {
        JwtTestSupport.stubUsuarioAtivo(usuarioSyncService);
    }

    @Nested
    @DisplayName("GET /authorize-url")
    class AuthorizeUrl {

        @Test
        @DisplayName("200 com a URL para ATLETA")
        void ok() throws Exception {
            when(atletaProgressService.resolverAtletaIdAtual()).thenReturn(atletaId);
            when(oauthService.gerarAuthorizeUrl(atletaId)).thenReturn("https://connect.garmin.com/oauth2Confirm?x=1");

            mockMvc.perform(get(BASE + "/authorize-url").with(atletaJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.authorizationUrl").value("https://connect.garmin.com/oauth2Confirm?x=1"));
        }

        @Test
        @DisplayName("503 quando o canal está desligado")
        void canalDesligado() throws Exception {
            when(atletaProgressService.resolverAtletaIdAtual()).thenReturn(atletaId);
            when(oauthService.gerarAuthorizeUrl(atletaId)).thenThrow(new GarminCanalIndisponivelException("off"));

            mockMvc.perform(get(BASE + "/authorize-url").with(atletaJwt()))
                    .andExpect(status().isServiceUnavailable());
        }

        @Test
        @DisplayName("403 para TECNICO e 401 sem token")
        void negados() throws Exception {
            mockMvc.perform(get(BASE + "/authorize-url").with(tecnicoJwt())).andExpect(status().isForbidden());
            mockMvc.perform(get(BASE + "/authorize-url")).andExpect(status().isUnauthorized());

            verifyNoInteractions(oauthService);
        }
    }

    @Nested
    @DisplayName("GET /")
    class Status {

        @Test
        @DisplayName("200 para ATLETA sem vazar token")
        void ok() throws Exception {
            when(atletaProgressService.resolverAtletaIdAtual()).thenReturn(atletaId);
            when(connectionService.status(atletaId, JwtTestSupport.TENANT_ID)).thenReturn(Optional.of(
                    new GarminConnectionStatusDto(true, "d331…4df8", true, false, null, null)));

            mockMvc.perform(get(BASE).with(atletaJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ativo").value(true))
                    .andExpect(jsonPath("$.workoutImport").value(true))
                    .andExpect(jsonPath("$.accessToken").doesNotExist());
        }

        @Test
        @DisplayName("404 quando nunca conectou")
        void nuncaConectou() throws Exception {
            when(atletaProgressService.resolverAtletaIdAtual()).thenReturn(atletaId);
            when(connectionService.status(atletaId, JwtTestSupport.TENANT_ID)).thenReturn(Optional.empty());

            mockMvc.perform(get(BASE).with(adminJwt())).andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("403 para TECNICO e 401 sem token")
        void negados() throws Exception {
            mockMvc.perform(get(BASE).with(tecnicoJwt())).andExpect(status().isForbidden());
            mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("DELETE /")
    class Desconectar {

        @Test
        @DisplayName("204 para ATLETA e delega ao service com o tenant do token")
        void ok() throws Exception {
            when(atletaProgressService.resolverAtletaIdAtual()).thenReturn(atletaId);

            mockMvc.perform(delete(BASE).with(atletaJwt())).andExpect(status().isNoContent());

            verify(connectionService).desconectar(atletaId, JwtTestSupport.TENANT_ID);
        }

        @Test
        @DisplayName("403 para TECNICO e 401 sem token")
        void negados() throws Exception {
            mockMvc.perform(delete(BASE).with(tecnicoJwt())).andExpect(status().isForbidden());
            mockMvc.perform(delete(BASE)).andExpect(status().isUnauthorized());

            verifyNoInteractions(connectionService);
        }
    }
}
