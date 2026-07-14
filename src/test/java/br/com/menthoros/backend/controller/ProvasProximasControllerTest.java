package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.config.core.JacksonConfig;
import br.com.menthoros.backend.dto.output.ProvasProximasResponseDto;
import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.repository.TenantValidationRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import br.com.menthoros.backend.services.ProvaService;
import br.com.menthoros.backend.services.UsuarioSyncService;
import br.com.menthoros.backend.testsupport.AuthWebMvcTestConfig;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProvasProximasController.class)
@Import({JacksonConfig.class, AuthWebMvcTestConfig.class})
class ProvasProximasControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProvaService provaService;

    @MockitoBean
    private TenantValidationRepository tenantValidationRepository;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private UsuarioSyncService usuarioSyncService;

    @MockitoBean
    private UsuarioRepository usuarioRepository;

    private static final UUID TENANT_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");

    @BeforeEach
    void stubUsuarioAtivo() {
        Usuario usuario = new Usuario();
        usuario.setAtivo(true);
        when(usuarioSyncService.syncUsuarioFromJwt(any(), any())).thenReturn(usuario);
    }

    private RequestPostProcessor tecnicoJwt() {
        return jwt()
                .authorities(new SimpleGrantedAuthority("ROLE_TECNICO"))
                .jwt(j -> j.claim("tenant_id", TENANT_ID.toString()).subject("tecnico-keycloak-id"));
    }

    private RequestPostProcessor atletaJwt() {
        return jwt()
                .authorities(new SimpleGrantedAuthority("ROLE_ATLETA"))
                .jwt(j -> j.claim("tenant_id", TENANT_ID.toString()).subject("atleta-keycloak-id"));
    }

    @Nested
    @DisplayName("getProvasProximas — GET /api/v1/provas/proximas")
    class GetProvasProximas {

        @Test
        @DisplayName("retorna 200 para TECNICO")
        void retorna200ParaTecnico() throws Exception {
            when(provaService.getProvasProximas())
                    .thenReturn(new ProvasProximasResponseDto(List.of(), 0, "2026-07-14T17:00:00"));

            mockMvc.perform(get("/api/v1/provas/proximas").with(tecnicoJwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(0));
        }

        @Test
        @DisplayName("retorna 403 para ATLETA (lista provas do tenant inteiro)")
        void retorna403ParaAtleta() throws Exception {
            mockMvc.perform(get("/api/v1/provas/proximas").with(atletaJwt()))
                    .andExpect(status().isForbidden());

            verify(provaService, never()).getProvasProximas();
        }

        @Test
        @DisplayName("retorna 401 quando requisição sem autenticação")
        void retorna401SemAutenticacao() throws Exception {
            mockMvc.perform(get("/api/v1/provas/proximas"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
