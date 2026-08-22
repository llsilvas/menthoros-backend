package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.config.core.JacksonConfig;
import br.com.menthoros.backend.dto.output.IntervalsIcuConnectionStatusDto;
import br.com.menthoros.backend.repository.TenantValidationRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import br.com.menthoros.backend.services.AtletaProgressService;
import br.com.menthoros.backend.services.IntervalsIcuConnectionService;
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
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static br.com.menthoros.backend.testsupport.JwtTestSupport.atletaJwt;
import static br.com.menthoros.backend.testsupport.JwtTestSupport.tecnicoJwt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Autorização do IntervalsIcuConnectionController (papel, sem @RequireTenant por ser endpoint
 * /me) com a cadeia de segurança real, e prova de que a resposta de status nunca vaza a key.
 */
@WebMvcTest(IntervalsIcuConnectionController.class)
@Import({JacksonConfig.class, AuthWebMvcTestConfig.class})
class IntervalsIcuConnectionControllerAuthTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IntervalsIcuConnectionService connectionService;

    @MockitoBean
    private br.com.menthoros.backend.services.IntervalsIcuOAuthService oauthService;

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
    @DisplayName("GET /api/v1/integracoes/me/intervals-icu/authorize-url")
    class AuthorizeUrl {

        @Test
        @DisplayName("retorna 200 com a URL para ATLETA")
        void retorna200ParaAtleta() throws Exception {
            when(oauthService.getAuthorizationUrl())
                    .thenReturn("https://intervals.icu/oauth/authorize?client_id=663&state=abc");

            mockMvc.perform(get("/api/v1/integracoes/me/intervals-icu/authorize-url").with(atletaJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.authorizationUrl")
                            .value("https://intervals.icu/oauth/authorize?client_id=663&state=abc"));
        }

        // ADMIN saiu do contrato: resolverAtletaIdAtual() exige Atleta vinculado ao Usuario, e um
        // ADMIN sem vínculo receberia 404 em vez da URL — CA1 descrevia caminho inalcançável.
        @Test
        @DisplayName("retorna 403 para TECNICO")
        void retorna403ParaTecnico() throws Exception {
            mockMvc.perform(get("/api/v1/integracoes/me/intervals-icu/authorize-url").with(tecnicoJwt()))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(oauthService);
        }

        @Test
        @DisplayName("retorna 401 sem token")
        void retorna401SemToken() throws Exception {
            mockMvc.perform(get("/api/v1/integracoes/me/intervals-icu/authorize-url"))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(oauthService);
        }

        @Test
        @DisplayName("a resposta não vaza o client_secret")
        void naoVazaSecret() throws Exception {
            when(oauthService.getAuthorizationUrl())
                    .thenReturn("https://intervals.icu/oauth/authorize?client_id=663&state=abc");

            String body = mockMvc.perform(
                            get("/api/v1/integracoes/me/intervals-icu/authorize-url").with(atletaJwt()))
                    .andReturn().getResponse().getContentAsString();

            assertThat(body).doesNotContain("client_secret");
        }
    }

    // CA8 — o fluxo de conexão por API key foi removido (D6). O que importa asserir é que o POST
    // com corpo {apiKey} não é mais aceito e não toca no service. O status é 405 e não 404 porque
    // a URL continua existindo para GET e DELETE: quem sumiu foi o método, não o recurso.
    @Nested
    @DisplayName("POST /api/v1/integracoes/me/intervals-icu (removido)")
    class ConectarPorApiKeyRemovido {

        @Test
        @DisplayName("POST com {apiKey} não é aceito e nada chega ao service")
        void postComApiKeyNaoExisteMais() throws Exception {
            mockMvc.perform(post("/api/v1/integracoes/me/intervals-icu")
                            .with(atletaJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"apiKey\":\"key-ok\"}"))
                    .andExpect(status().isMethodNotAllowed());

            verifyNoInteractions(connectionService, oauthService);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/integracoes/me/intervals-icu")
    class Status {

        @Test
        @DisplayName("retorna 200 para ATLETA e nunca inclui apiKey/accessToken na resposta")
        void retorna200ParaAtletaSemVazarKey() throws Exception {
            when(atletaProgressService.resolverAtletaIdAtual()).thenReturn(atletaId);
            when(connectionService.status(atletaId))
                    .thenReturn(Optional.of(new IntervalsIcuConnectionStatusDto(
                            true, "i641775", Instant.parse("2026-07-01T00:00:00Z"), null, null)));

            mockMvc.perform(get("/api/v1/integracoes/me/intervals-icu").with(atletaJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.apiKey").doesNotExist())
                    .andExpect(jsonPath("$.accessToken").doesNotExist())
                    .andExpect(jsonPath("$.conectado").value(true))
                    .andExpect(jsonPath("$.externalAthleteId").value("i641775"));
        }

        @Test
        @DisplayName("retorna 403 para TECNICO")
        void retorna403ParaTecnico() throws Exception {
            mockMvc.perform(get("/api/v1/integracoes/me/intervals-icu").with(tecnicoJwt()))
                    .andExpect(status().isForbidden());

            verify(connectionService, never()).status(any());
        }

        @Test
        @DisplayName("retorna 401 sem token")
        void retorna401SemToken() throws Exception {
            mockMvc.perform(get("/api/v1/integracoes/me/intervals-icu"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/integracoes/me/intervals-icu")
    class Desconectar {

        // Passa pelo OAuth service, não direto no connectionService: a revogação remota precisa
        // acontecer antes do soft-disconnect local, enquanto o token ainda existe (D7).
        @Test
        @DisplayName("retorna 204 para ATLETA e revoga no provedor")
        void retorna204ParaAtleta() throws Exception {
            when(atletaProgressService.resolverAtletaIdAtual()).thenReturn(atletaId);

            mockMvc.perform(delete("/api/v1/integracoes/me/intervals-icu").with(atletaJwt()))
                    .andExpect(status().isNoContent());

            verify(oauthService).revogarEDesconectar(atletaId);
        }

        @Test
        @DisplayName("retorna 403 para TECNICO")
        void retorna403ParaTecnico() throws Exception {
            mockMvc.perform(delete("/api/v1/integracoes/me/intervals-icu").with(tecnicoJwt()))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(connectionService, oauthService);
        }

        @Test
        @DisplayName("retorna 401 sem token")
        void retorna401SemToken() throws Exception {
            mockMvc.perform(delete("/api/v1/integracoes/me/intervals-icu"))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(connectionService, oauthService);
        }
    }
}
