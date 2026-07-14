package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.config.core.JacksonConfig;
import br.com.menthoros.backend.dto.output.AdesaoDiariaDto;
import br.com.menthoros.backend.dto.output.AdesaoSemanalDto;
import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.repository.TenantValidationRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import br.com.menthoros.backend.services.MetricasAdesaoService;
import br.com.menthoros.backend.services.UsuarioSyncService;
import br.com.menthoros.backend.testsupport.AuthWebMvcTestConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MetricasController.class)
@Import({JacksonConfig.class, AuthWebMvcTestConfig.class})
class MetricasControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MetricasAdesaoService metricasAdesaoService;

    @MockitoBean
    private TenantValidationRepository tenantValidationRepository;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private UsuarioSyncService usuarioSyncService;

    @MockitoBean
    private UsuarioRepository usuarioRepository;

    private static final UUID TENANT_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private final UUID atletaId = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

    private RequestPostProcessor tecnicoJwt() {
        return jwt()
                .authorities(new SimpleGrantedAuthority("ROLE_TECNICO"))
                .jwt(j -> j.claim("tenant_id", TENANT_ID.toString()).subject("tecnico-keycloak-id"));
    }

    private RequestPostProcessor adminJwt() {
        return jwt()
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))
                .jwt(j -> j.claim("tenant_id", TENANT_ID.toString()).subject("admin-keycloak-id"));
    }

    private RequestPostProcessor atletaJwt() {
        return jwt()
                .authorities(new SimpleGrantedAuthority("ROLE_ATLETA"))
                .jwt(j -> j.claim("tenant_id", TENANT_ID.toString()).subject("atleta-keycloak-id"));
    }

    private void stubAtletaNoTenant() {
        when(tenantValidationRepository.resourceBelongsToTenant(atletaId, TENANT_ID)).thenReturn(true);
    }

    @org.junit.jupiter.api.BeforeEach
    void stubUsuarioAtivo() {
        Usuario usuario = new Usuario();
        usuario.setAtivo(true);
        when(usuarioSyncService.syncUsuarioFromJwt(any(), any())).thenReturn(usuario);
    }

    @Nested
    @DisplayName("getAdesaoSemanal — GET /adesao-semanal")
    class GetAdesaoSemanal {

        @Test
        @DisplayName("retorna 200 para TECNICO com atleta do tenant")
        void retorna200ParaTecnico() throws Exception {
            stubAtletaNoTenant();
            when(metricasAdesaoService.getAdesaoSemanal(atletaId.toString()))
                    .thenReturn(new AdesaoSemanalDto(atletaId.toString(), "Atleta Teste", null, List.of(), 0.0));

            mockMvc.perform(get("/api/v1/atletas/{atletaId}/metricas/adesao-semanal", atletaId)
                            .with(tecnicoJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.atletaId").value(atletaId.toString()));
        }

        @Test
        @DisplayName("retorna 200 para ADMIN com atleta do tenant")
        void retorna200ParaAdmin() throws Exception {
            stubAtletaNoTenant();
            when(metricasAdesaoService.getAdesaoSemanal(atletaId.toString()))
                    .thenReturn(new AdesaoSemanalDto(atletaId.toString(), "Atleta Teste", null, List.of(), 0.0));

            mockMvc.perform(get("/api/v1/atletas/{atletaId}/metricas/adesao-semanal", atletaId)
                            .with(adminJwt()))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("retorna 403 para ATLETA (endpoint de consumo do coach)")
        void retorna403ParaAtleta() throws Exception {
            mockMvc.perform(get("/api/v1/atletas/{atletaId}/metricas/adesao-semanal", atletaId)
                            .with(atletaJwt()))
                    .andExpect(status().isForbidden());

            verify(metricasAdesaoService, never()).getAdesaoSemanal(anyString());
        }

        @Test
        @DisplayName("retorna 401 quando requisição sem autenticação")
        void retorna401SemAutenticacao() throws Exception {
            mockMvc.perform(get("/api/v1/atletas/{atletaId}/metricas/adesao-semanal", atletaId))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("retorna 403 quando atleta pertence a outro tenant")
        void retorna403CrossTenant() throws Exception {
            when(tenantValidationRepository.resourceBelongsToTenant(any(), any())).thenReturn(false);

            mockMvc.perform(get("/api/v1/atletas/{atletaId}/metricas/adesao-semanal", atletaId)
                            .with(tecnicoJwt()))
                    .andExpect(status().isForbidden());

            verify(metricasAdesaoService, never()).getAdesaoSemanal(anyString());
        }
    }

    @Nested
    @DisplayName("getAdesaoDiaria — GET /adesao-diaria")
    class GetAdesaoDiaria {

        @Test
        @DisplayName("retorna 200 para TECNICO com atleta do tenant")
        void retorna200ParaTecnico() throws Exception {
            stubAtletaNoTenant();
            when(metricasAdesaoService.getAdesaoDiaria(atletaId.toString()))
                    .thenReturn(new AdesaoDiariaDto(atletaId.toString(), "Atleta Teste", List.of()));

            mockMvc.perform(get("/api/v1/atletas/{atletaId}/metricas/adesao-diaria", atletaId)
                            .with(tecnicoJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.atletaId").value(atletaId.toString()));
        }

        @Test
        @DisplayName("retorna 403 para ATLETA")
        void retorna403ParaAtleta() throws Exception {
            mockMvc.perform(get("/api/v1/atletas/{atletaId}/metricas/adesao-diaria", atletaId)
                            .with(atletaJwt()))
                    .andExpect(status().isForbidden());

            verify(metricasAdesaoService, never()).getAdesaoDiaria(anyString());
        }

        @Test
        @DisplayName("retorna 403 quando atleta pertence a outro tenant")
        void retorna403CrossTenant() throws Exception {
            when(tenantValidationRepository.resourceBelongsToTenant(any(), any())).thenReturn(false);

            mockMvc.perform(get("/api/v1/atletas/{atletaId}/metricas/adesao-diaria", atletaId)
                            .with(tecnicoJwt()))
                    .andExpect(status().isForbidden());

            verify(metricasAdesaoService, never()).getAdesaoDiaria(anyString());
        }
    }
}
