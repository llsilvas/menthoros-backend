package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.config.core.JacksonConfig;
import br.com.menthoros.backend.dto.output.WaitlistFunnelBucketOutputDto;
import br.com.menthoros.backend.repository.TenantValidationRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import br.com.menthoros.backend.services.UsuarioSyncService;
import br.com.menthoros.backend.services.WaitlistFunnelService;
import br.com.menthoros.backend.testsupport.AuthWebMvcTestConfig;
import org.junit.jupiter.api.DisplayName;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WaitlistFunnelController.class)
@Import({JacksonConfig.class, AuthWebMvcTestConfig.class})
@DisplayName("GET /api/admin/waitlist/funnel")
class WaitlistFunnelControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private WaitlistFunnelService waitlistFunnelService;
    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private UsuarioSyncService usuarioSyncService;
    @MockitoBean private UsuarioRepository usuarioRepository;
    @MockitoBean private TenantValidationRepository tenantValidationRepository;

    private static RequestPostProcessor jwtCom(String papel) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + papel));
    }

    @Test
    @DisplayName("ADMIN → 200 com os grupos do funil")
    void adminVeOFunil() throws Exception {
        when(waitlistFunnelService.calcularFunil(any(), any())).thenReturn(
                List.of(new WaitlistFunnelBucketOutputDto("instagram", "bio-link", 2, 1, 1, 0)));

        mockMvc.perform(get("/api/admin/waitlist/funnel").with(jwtCom("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].utmSource").value("instagram"))
                .andExpect(jsonPath("$[0].total").value(2))
                .andExpect(jsonPath("$[0].qualified").value(1));
    }

    @Test
    @DisplayName("TECNICO → 403")
    void tecnicoNegado() throws Exception {
        mockMvc.perform(get("/api/admin/waitlist/funnel").with(jwtCom("TECNICO")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("sem JWT → 401")
    void semAutenticacao() throws Exception {
        mockMvc.perform(get("/api/admin/waitlist/funnel"))
                .andExpect(status().isUnauthorized());
    }
}
