package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.config.core.JacksonConfig;
import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.exception.DomainConflictException;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.repository.TenantValidationRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import br.com.menthoros.backend.services.PlanoReviewService;
import br.com.menthoros.backend.services.UsuarioSyncService;
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

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato HTTP de {@code POST /api/v1/coach/planos/{id}/reenviar-sincronizacao}, com a cadeia de
 * segurança real ({@code @PreAuthorize} e {@code @RequireTenant}) via {@link AuthWebMvcTestConfig}.
 */
@WebMvcTest(CoachPlanoReviewController.class)
@Import({JacksonConfig.class, AuthWebMvcTestConfig.class})
class CoachPlanoReviewControllerReenvioTest {

    private static final UUID TENANT_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final String URL = "/api/v1/coach/planos/{id}/reenviar-sincronizacao";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private PlanoReviewService planoReviewService;
    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private UsuarioSyncService usuarioSyncService;
    @MockitoBean private UsuarioRepository usuarioRepository;
    @MockitoBean private TenantValidationRepository tenantValidationRepository;

    private final UUID planoId = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

    @Test
    @DisplayName("200 com a contagem de treinos reenviados")
    void retorna200ComContagem() throws Exception {
        stubUsuarioAtivo();
        when(planoReviewService.reenviarSincronizacao(planoId, TENANT_ID)).thenReturn(4);

        mockMvc.perform(post(URL, planoId).with(jwtComPapel("TECNICO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resent").value(4));
    }

    @Test
    @DisplayName("200 com reenviados = 0 quando nada está em erro")
    void retorna200ComZero() throws Exception {
        stubUsuarioAtivo();
        when(planoReviewService.reenviarSincronizacao(planoId, TENANT_ID)).thenReturn(0);

        mockMvc.perform(post(URL, planoId).with(jwtComPapel("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resent").value(0));
    }

    @Test
    @DisplayName("403 para atleta")
    void retorna403ParaAtleta() throws Exception {
        stubUsuarioAtivo();

        mockMvc.perform(post(URL, planoId).with(jwtComPapel("ATLETA")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(planoReviewService);
    }

    @Test
    @DisplayName("403 para plano de outro tenant (CA4)")
    void retorna403ParaOutroTenant() throws Exception {
        stubUsuarioAtivo();
        when(tenantValidationRepository.resourceBelongsToTenant(planoId, TENANT_ID)).thenReturn(false);

        mockMvc.perform(post(URL, planoId).with(jwtComPapel("TECNICO")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(planoReviewService);
    }

    @Test
    @DisplayName("404 quando o plano não existe no tenant")
    void retorna404() throws Exception {
        stubUsuarioAtivo();
        when(planoReviewService.reenviarSincronizacao(eq(planoId), any()))
                .thenThrow(new DomainNotFoundException("Plano não encontrado"));

        mockMvc.perform(post(URL, planoId).with(jwtComPapel("TECNICO")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("409 com push em andamento")
    void retorna409() throws Exception {
        stubUsuarioAtivo();
        when(planoReviewService.reenviarSincronizacao(eq(planoId), any()))
                .thenThrow(new DomainConflictException("Envio em andamento."));

        mockMvc.perform(post(URL, planoId).with(jwtComPapel("TECNICO")))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("422 quando o plano não está aprovado")
    void retorna422() throws Exception {
        stubUsuarioAtivo();
        when(planoReviewService.reenviarSincronizacao(eq(planoId), any()))
                .thenThrow(new DomainRuleViolationException("Só é possível reenviar um plano aprovado"));

        mockMvc.perform(post(URL, planoId).with(jwtComPapel("TECNICO")))
                .andExpect(status().isUnprocessableEntity());
    }

    private RequestPostProcessor jwtComPapel(String papel) {
        return jwt()
                .authorities(new SimpleGrantedAuthority("ROLE_" + papel))
                .jwt(j -> j.claim("tenant_id", TENANT_ID.toString()).subject("usuario-keycloak-id"));
    }

    private void stubUsuarioAtivo() {
        Usuario usuario = new Usuario();
        usuario.setAtivo(true);
        when(usuarioSyncService.syncUsuarioFromJwt(any(), any())).thenReturn(usuario);
        when(tenantValidationRepository.resourceBelongsToTenant(planoId, TENANT_ID)).thenReturn(true);
    }
}
