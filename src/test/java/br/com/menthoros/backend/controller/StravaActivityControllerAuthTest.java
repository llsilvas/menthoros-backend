package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.config.core.JacksonConfig;
import br.com.menthoros.backend.dto.output.StravaSyncStatusDto;
import br.com.menthoros.backend.repository.TenantValidationRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import br.com.menthoros.backend.services.StravaActivityService;
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

import java.util.UUID;

import static br.com.menthoros.backend.testsupport.JwtTestSupport.atletaJwt;
import static br.com.menthoros.backend.testsupport.JwtTestSupport.tecnicoJwt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Autorização do StravaActivityController (papel + tenant) com a cadeia de segurança real.
 * O comportamento funcional dos endpoints está em {@link StravaActivityControllerTest} (unit).
 */
@WebMvcTest(StravaActivityController.class)
@Import({JacksonConfig.class, AuthWebMvcTestConfig.class})
class StravaActivityControllerAuthTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StravaActivityService stravaActivityService;

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
    @DisplayName("sync — POST /api/v1/strava/sync/{atletaId}")
    class Sync {

        @Test
        @DisplayName("retorna 200 para TECNICO com atleta do tenant")
        void retorna200ParaTecnico() throws Exception {
            stubAtletaNoTenant();

            mockMvc.perform(post("/api/v1/strava/sync/{atletaId}", atletaId).with(tecnicoJwt()))
                    .andExpect(status().isOk());

            verify(stravaActivityService).syncActivitiesForAtleta(atletaId, TENANT_ID);
        }

        @Test
        @DisplayName("retorna 403 para ATLETA")
        void retorna403ParaAtleta() throws Exception {
            mockMvc.perform(post("/api/v1/strava/sync/{atletaId}", atletaId).with(atletaJwt()))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(stravaActivityService);
        }

        @Test
        @DisplayName("retorna 403 quando atleta pertence a outro tenant")
        void retorna403CrossTenant() throws Exception {
            when(tenantValidationRepository.resourceBelongsToTenant(any(), any())).thenReturn(false);

            mockMvc.perform(post("/api/v1/strava/sync/{atletaId}", atletaId).with(tecnicoJwt()))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(stravaActivityService);
        }

        @Test
        @DisplayName("retorna 401 quando requisição sem autenticação")
        void retorna401SemAutenticacao() throws Exception {
            mockMvc.perform(post("/api/v1/strava/sync/{atletaId}", atletaId))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("getSyncStatus — GET /api/v1/strava/sync-status/{atletaId}")
    class GetSyncStatus {

        @Test
        @DisplayName("retorna 200 para TECNICO com atleta do tenant")
        void retorna200ParaTecnico() throws Exception {
            stubAtletaNoTenant();
            when(stravaActivityService.getSyncStatus(atletaId, TENANT_ID))
                    .thenReturn(new StravaSyncStatusDto(true, false, 0, null, null, "123456789"));

            mockMvc.perform(get("/api/v1/strava/sync-status/{atletaId}", atletaId).with(tecnicoJwt()))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("retorna 403 para ATLETA")
        void retorna403ParaAtleta() throws Exception {
            mockMvc.perform(get("/api/v1/strava/sync-status/{atletaId}", atletaId).with(atletaJwt()))
                    .andExpect(status().isForbidden());

            verify(stravaActivityService, never()).getSyncStatus(any(), any());
        }

        @Test
        @DisplayName("retorna 403 quando atleta pertence a outro tenant")
        void retorna403CrossTenant() throws Exception {
            when(tenantValidationRepository.resourceBelongsToTenant(any(), any())).thenReturn(false);

            mockMvc.perform(get("/api/v1/strava/sync-status/{atletaId}", atletaId).with(tecnicoJwt()))
                    .andExpect(status().isForbidden());

            verify(stravaActivityService, never()).getSyncStatus(any(), any());
        }
    }
}
