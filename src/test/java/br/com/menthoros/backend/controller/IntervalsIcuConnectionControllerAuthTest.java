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
    @DisplayName("POST /api/v1/integracoes/me/intervals-icu")
    class Conectar {

        @Test
        @DisplayName("retorna 201 para ATLETA com key válida")
        void retorna201ParaAtleta() throws Exception {
            when(atletaProgressService.resolverAtletaIdAtual()).thenReturn(atletaId);
            when(connectionService.conectar(atletaId, "key-ok"))
                    .thenReturn(new IntervalsIcuConnectionStatusDto(true, "i641775", Instant.now(), null, null));

            mockMvc.perform(post("/api/v1/integracoes/me/intervals-icu")
                            .with(atletaJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"apiKey\":\"key-ok\"}"))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("retorna 403 para TECNICO")
        void retorna403ParaTecnico() throws Exception {
            mockMvc.perform(post("/api/v1/integracoes/me/intervals-icu")
                            .with(tecnicoJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"apiKey\":\"key-ok\"}"))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(connectionService);
        }

        @Test
        @DisplayName("retorna 401 sem token")
        void retorna401SemToken() throws Exception {
            mockMvc.perform(post("/api/v1/integracoes/me/intervals-icu")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"apiKey\":\"key-ok\"}"))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(connectionService);
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

        @Test
        @DisplayName("retorna 204 para ATLETA")
        void retorna204ParaAtleta() throws Exception {
            when(atletaProgressService.resolverAtletaIdAtual()).thenReturn(atletaId);

            mockMvc.perform(delete("/api/v1/integracoes/me/intervals-icu").with(atletaJwt()))
                    .andExpect(status().isNoContent());

            verify(connectionService).desconectar(atletaId);
        }

        @Test
        @DisplayName("retorna 403 para TECNICO")
        void retorna403ParaTecnico() throws Exception {
            mockMvc.perform(delete("/api/v1/integracoes/me/intervals-icu").with(tecnicoJwt()))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(connectionService);
        }

        @Test
        @DisplayName("retorna 401 sem token")
        void retorna401SemToken() throws Exception {
            mockMvc.perform(delete("/api/v1/integracoes/me/intervals-icu"))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(connectionService);
        }
    }
}
