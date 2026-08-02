package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.config.core.JacksonConfig;
import br.com.menthoros.backend.dto.output.TreinoRealizadoOutputDto;
import br.com.menthoros.backend.repository.TenantValidationRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import br.com.menthoros.backend.services.IntervalsIcuActivityIngestionService;
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

import br.com.menthoros.backend.dto.output.BackfillEtapasOutputDto;

import java.util.UUID;

import static br.com.menthoros.backend.testsupport.JwtTestSupport.atletaJwt;
import static br.com.menthoros.backend.testsupport.JwtTestSupport.tecnicoJwt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Autorização do {@link IntervalsIcuActivityController} (papel + tenant) com a cadeia de segurança
 * real, seguindo o padrão de {@link StravaActivityControllerAuthTest}.
 */
@WebMvcTest(IntervalsIcuActivityController.class)
@Import({JacksonConfig.class, AuthWebMvcTestConfig.class})
class IntervalsIcuActivityControllerAuthTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IntervalsIcuActivityIngestionService intervalsIcuActivityIngestionService;

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

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private br.com.menthoros.backend.services.IntervalsIcuLapsBackfillService intervalsIcuLapsBackfillService;

    @BeforeEach
    void stubUsuarioAtivo() {
        JwtTestSupport.stubUsuarioAtivo(usuarioSyncService);
    }

    private void stubAtletaNoTenant() {
        when(tenantValidationRepository.resourceBelongsToTenant(atletaId, TENANT_ID)).thenReturn(true);
    }

    @Nested
    @DisplayName("backfillEtapas — POST /api/v1/intervals-icu/atletas/{atletaId}/activities/backfill-laps")
    class BackfillEtapas {

        @Test
        @DisplayName("retorna 200 para TECNICO com atleta do tenant")
        void retorna200ParaTecnico() throws Exception {
            stubAtletaNoTenant();
            when(intervalsIcuLapsBackfillService.backfillEtapas(atletaId, TENANT_ID))
                    .thenReturn(new BackfillEtapasOutputDto(3, 2, 1, 0, 0));

            mockMvc.perform(post("/api/v1/intervals-icu/atletas/{atletaId}/activities/backfill-laps", atletaId)
                            .with(tecnicoJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.candidatos").value(3))
                    .andExpect(jsonPath("$.atualizados").value(2))
                    .andExpect(jsonPath("$.semIntervalos").value(1));
        }

        @Test
        @DisplayName("retorna 403 para ATLETA — backfill e acao do coach")
        void retorna403ParaAtleta() throws Exception {
            mockMvc.perform(post("/api/v1/intervals-icu/atletas/{atletaId}/activities/backfill-laps", atletaId)
                            .with(atletaJwt()))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(intervalsIcuLapsBackfillService);
        }

        @Test
        @DisplayName("retorna 401 sem autenticacao")
        void retorna401SemAutenticacao() throws Exception {
            mockMvc.perform(post("/api/v1/intervals-icu/atletas/{atletaId}/activities/backfill-laps", atletaId))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(intervalsIcuLapsBackfillService);
        }

        @Test
        @DisplayName("retorna 403 quando o atleta e de outro tenant")
        void retorna403CrossTenant() throws Exception {
            when(tenantValidationRepository.resourceBelongsToTenant(any(), any())).thenReturn(false);

            mockMvc.perform(post("/api/v1/intervals-icu/atletas/{atletaId}/activities/backfill-laps", atletaId)
                            .with(tecnicoJwt()))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(intervalsIcuLapsBackfillService);
        }
    }

    @Nested
    @DisplayName("importarAtividade — POST /api/v1/intervals-icu/atletas/{atletaId}/activities/import")
    class ImportarAtividade {

        @Test
        @DisplayName("retorna 200 para TECNICO com atleta do tenant")
        void retorna200ParaTecnico() throws Exception {
            stubAtletaNoTenant();
            when(intervalsIcuActivityIngestionService.importarAtividade(eq(atletaId), eq("i166338796"), eq(TENANT_ID)))
                    .thenReturn(mockOutputDto());

            mockMvc.perform(post("/api/v1/intervals-icu/atletas/{atletaId}/activities/import", atletaId)
                            .param("activityId", "i166338796")
                            .with(tecnicoJwt()))
                    .andExpect(status().isOk());

            verify(intervalsIcuActivityIngestionService).importarAtividade(atletaId, "i166338796", TENANT_ID);
        }

        @Test
        @DisplayName("retorna 200 para ADMIN com atleta do tenant")
        void retorna200ParaAdmin() throws Exception {
            stubAtletaNoTenant();
            when(intervalsIcuActivityIngestionService.importarAtividade(eq(atletaId), eq("i166338796"), eq(TENANT_ID)))
                    .thenReturn(mockOutputDto());

            mockMvc.perform(post("/api/v1/intervals-icu/atletas/{atletaId}/activities/import", atletaId)
                            .param("activityId", "i166338796")
                            .with(JwtTestSupport.adminJwt()))
                    .andExpect(status().isOk());

            verify(intervalsIcuActivityIngestionService).importarAtividade(atletaId, "i166338796", TENANT_ID);
        }

        @Test
        @DisplayName("retorna 403 para ATLETA")
        void retorna403ParaAtleta() throws Exception {
            mockMvc.perform(post("/api/v1/intervals-icu/atletas/{atletaId}/activities/import", atletaId)
                            .param("activityId", "i166338796")
                            .with(atletaJwt()))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(intervalsIcuActivityIngestionService);
        }

        @Test
        @DisplayName("retorna 401 quando requisição sem autenticação")
        void retorna401SemAutenticacao() throws Exception {
            mockMvc.perform(post("/api/v1/intervals-icu/atletas/{atletaId}/activities/import", atletaId)
                            .param("activityId", "i166338796"))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(intervalsIcuActivityIngestionService);
        }

        @Test
        @DisplayName("retorna 403 quando atleta pertence a outro tenant")
        void retorna403CrossTenant() throws Exception {
            when(tenantValidationRepository.resourceBelongsToTenant(any(), any())).thenReturn(false);

            mockMvc.perform(post("/api/v1/intervals-icu/atletas/{atletaId}/activities/import", atletaId)
                            .param("activityId", "i166338796")
                            .with(tecnicoJwt()))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(intervalsIcuActivityIngestionService);
        }

        @Test
        @DisplayName("repassa activityId colado como URL completa intacto ao service (normalização é do service)")
        void repassaActivityIdComoUrlCompletaIntacto() throws Exception {
            stubAtletaNoTenant();
            String urlColada = "https://intervals.icu/activities/i166338796";
            when(intervalsIcuActivityIngestionService.importarAtividade(eq(atletaId), eq(urlColada), eq(TENANT_ID)))
                    .thenReturn(mockOutputDto());

            mockMvc.perform(post("/api/v1/intervals-icu/atletas/{atletaId}/activities/import", atletaId)
                            .param("activityId", urlColada)
                            .with(tecnicoJwt()))
                    .andExpect(status().isOk());

            verify(intervalsIcuActivityIngestionService).importarAtividade(atletaId, urlColada, TENANT_ID);
        }

        @Test
        @DisplayName("retorna 400 quando o service rejeita activityId inválido (IllegalArgumentException)")
        void retorna400QuandoActivityIdInvalido() throws Exception {
            stubAtletaNoTenant();
            when(intervalsIcuActivityIngestionService.importarAtividade(eq(atletaId), eq("a/b?c"), eq(TENANT_ID)))
                    .thenThrow(new IllegalArgumentException("activityId inválido: a/b?c"));

            mockMvc.perform(post("/api/v1/intervals-icu/atletas/{atletaId}/activities/import", atletaId)
                            .param("activityId", "a/b?c")
                            .with(tecnicoJwt()))
                    .andExpect(status().isBadRequest());
        }
    }

    private TreinoRealizadoOutputDto mockOutputDto() {
        return org.mockito.Mockito.mock(TreinoRealizadoOutputDto.class);
    }
}
