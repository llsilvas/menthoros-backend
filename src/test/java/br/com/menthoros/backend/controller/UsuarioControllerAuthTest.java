package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.config.core.JacksonConfig;
import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.repository.TenantValidationRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import br.com.menthoros.backend.services.UsuarioService;
import br.com.menthoros.backend.services.UsuarioSyncService;
import br.com.menthoros.backend.testsupport.AuthWebMvcTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Autorização do /users/me com a cadeia real: qualquer papel autenticado acessa
 * (isAuthenticated() — endpoint de identidade), anônimo recebe 401.
 */
@WebMvcTest(UsuarioController.class)
@Import({JacksonConfig.class, AuthWebMvcTestConfig.class})
class UsuarioControllerAuthTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UsuarioService usuarioService;

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

    @Test
    @DisplayName("retorna 200 para qualquer papel autenticado (identidade é self-service)")
    void retorna200ParaAtletaAutenticado() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .with(jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_ATLETA"))
                                .jwt(j -> j.claim("tenant_id", TENANT_ID.toString()).subject("atleta-kc"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("retorna 401 quando requisição sem autenticação")
    void retorna401SemAutenticacao() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }
}
