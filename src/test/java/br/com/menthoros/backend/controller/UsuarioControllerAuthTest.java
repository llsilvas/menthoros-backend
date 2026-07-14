package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.config.core.JacksonConfig;
import br.com.menthoros.backend.repository.TenantValidationRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import br.com.menthoros.backend.services.UsuarioService;
import br.com.menthoros.backend.services.UsuarioSyncService;
import br.com.menthoros.backend.testsupport.AuthWebMvcTestConfig;
import br.com.menthoros.backend.testsupport.JwtTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static br.com.menthoros.backend.testsupport.JwtTestSupport.atletaJwt;
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

    @BeforeEach
    void stubUsuarioAtivo() {
        JwtTestSupport.stubUsuarioAtivo(usuarioSyncService);
    }

    @Test
    @DisplayName("retorna 200 para qualquer papel autenticado (identidade é self-service)")
    void retorna200ParaAtletaAutenticado() throws Exception {
        mockMvc.perform(get("/api/v1/users/me").with(atletaJwt()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("retorna 401 quando requisição sem autenticação")
    void retorna401SemAutenticacao() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }
}
