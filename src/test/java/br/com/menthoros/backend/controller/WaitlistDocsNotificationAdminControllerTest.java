package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.config.core.JacksonConfig;
import br.com.menthoros.backend.dto.output.WaitlistDocsNotificationResultDto;
import br.com.menthoros.backend.repository.TenantValidationRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import br.com.menthoros.backend.services.UsuarioSyncService;
import br.com.menthoros.backend.services.WaitlistDocsNotificationService;
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

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WaitlistDocsNotificationAdminController.class)
@Import({JacksonConfig.class, AuthWebMvcTestConfig.class})
@DisplayName("POST /api/admin/waitlist/notificar-docs")
class WaitlistDocsNotificationAdminControllerTest {

    private static final String ROTA = "/api/admin/waitlist/notificar-docs";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private WaitlistDocsNotificationService service;
    // O slice arrasta o JwtTenantFilter (@Component) e, com ele, estes dois. A rota é
    // /api/admin/**, isenta do filtro — os mocks só existem para o contexto subir.
    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private UsuarioSyncService usuarioSyncService;
    @MockitoBean private UsuarioRepository usuarioRepository;
    @MockitoBean private TenantValidationRepository tenantValidationRepository;

    private static RequestPostProcessor jwtCom(String papel) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + papel));
    }

    @Test
    @DisplayName("ADMIN → 200 com a contagem do disparo")
    void adminDispara() throws Exception {
        when(service.notificar()).thenReturn(new WaitlistDocsNotificationResultDto(3, 2, 1));

        mockMvc.perform(post(ROTA).with(csrf()).with(jwtCom("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.elegiveis").value(3))
                .andExpect(jsonPath("$.enviados").value(2))
                .andExpect(jsonPath("$.falhas").value(1));
    }

    @Test
    @DisplayName("CA6 — TECNICO → 403, sem chamar o serviço")
    void tecnicoNegado() throws Exception {
        mockMvc.perform(post(ROTA).with(csrf()).with(jwtCom("TECNICO")))
                .andExpect(status().isForbidden());

        verify(service, never()).notificar();
    }

    @Test
    @DisplayName("CA6 — PROPRIETARIO (dono de assessoria) → 403 — ADMIN é role de staff, não de tenant")
    void proprietarioNegado() throws Exception {
        mockMvc.perform(post(ROTA).with(csrf()).with(jwtCom("PROPRIETARIO")))
                .andExpect(status().isForbidden());

        verify(service, never()).notificar();
    }

    @Test
    @DisplayName("sem JWT → 401")
    void semAutenticacao() throws Exception {
        mockMvc.perform(post(ROTA).with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CA5 — nenhum elegível → 200 com todas as contagens zeradas")
    void semElegiveis() throws Exception {
        when(service.notificar()).thenReturn(new WaitlistDocsNotificationResultDto(0, 0, 0));

        mockMvc.perform(post(ROTA).with(csrf()).with(jwtCom("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.elegiveis").value(0))
                .andExpect(jsonPath("$.enviados").value(0))
                .andExpect(jsonPath("$.falhas").value(0));
    }
}
