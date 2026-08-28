package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.config.core.JacksonConfig;
import br.com.menthoros.backend.dto.output.FoundingInviteOutputDto;
import br.com.menthoros.backend.exception.DomainConflictException;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.exception.EmailDeliveryException;
import br.com.menthoros.backend.repository.UsuarioRepository;
import br.com.menthoros.backend.services.FoundingInviteService;
import br.com.menthoros.backend.services.UsuarioSyncService;
import br.com.menthoros.backend.testsupport.AuthWebMvcTestConfig;
import br.com.menthoros.backend.repository.TenantValidationRepository;
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

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FoundingInviteAdminController.class)
@Import({JacksonConfig.class, AuthWebMvcTestConfig.class})
@DisplayName("POST /api/admin/waitlist/{id}/convite")
class FoundingInviteAdminControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private FoundingInviteService foundingInviteService;
    // O slice arrasta o JwtTenantFilter (@Component) e, com ele, estes dois. A rota é /api/admin/**,
    // isenta do filtro — os mocks só existem para o contexto subir.
    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private UsuarioSyncService usuarioSyncService;
    @MockitoBean private UsuarioRepository usuarioRepository;
    @MockitoBean private TenantValidationRepository tenantValidationRepository;

    private final UUID waitlistId = UUID.randomUUID();

    private static RequestPostProcessor jwtCom(String papel, String subject) {
        return jwt()
                .authorities(new SimpleGrantedAuthority("ROLE_" + papel))
                .jwt(j -> j.subject(subject));
    }

    private String rota() {
        return "/api/admin/waitlist/" + waitlistId + "/convite";
    }

    @Test
    @DisplayName("ADMIN → 202 com id, waitlistId e expiresAt — e nenhum token no corpo")
    void adminConvida() throws Exception {
        var expira = OffsetDateTime.parse("2026-09-04T12:00:00Z");
        var id = UUID.randomUUID();
        when(foundingInviteService.invite(eq(waitlistId), eq("admin-sub")))
                .thenReturn(new FoundingInviteOutputDto(id, waitlistId, expira));

        var corpo = mockMvc.perform(post(rota()).with(csrf()).with(jwtCom("ADMIN", "admin-sub")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.waitlistId").value(waitlistId.toString()))
                .andExpect(jsonPath("$.expiresAt").exists())
                .andReturn().getResponse().getContentAsString();

        assertThat(corpo).doesNotContainIgnoringCase("token");
    }

    @Test
    @DisplayName("o subject do JWT é quem fica registrado como emissor")
    void registraOEmissor() throws Exception {
        when(foundingInviteService.invite(any(), any()))
                .thenReturn(new FoundingInviteOutputDto(UUID.randomUUID(), waitlistId, OffsetDateTime.now()));

        mockMvc.perform(post(rota()).with(csrf()).with(jwtCom("ADMIN", "founder-uuid")))
                .andExpect(status().isAccepted());

        verify(foundingInviteService).invite(waitlistId, "founder-uuid");
    }

    @Test
    @DisplayName("TECNICO → 403, sem chamar o serviço")
    void tecnicoNegado() throws Exception {
        mockMvc.perform(post(rota()).with(csrf()).with(jwtCom("TECNICO", "tec-sub")))
                .andExpect(status().isForbidden());

        verify(foundingInviteService, never()).invite(any(), any());
    }

    @Test
    @DisplayName("PROPRIETARIO (dono de assessoria) → 403 — ADMIN é role de staff, não de tenant")
    void proprietarioNegado() throws Exception {
        mockMvc.perform(post(rota()).with(csrf()).with(jwtCom("PROPRIETARIO", "dono-sub")))
                .andExpect(status().isForbidden());

        verify(foundingInviteService, never()).invite(any(), any());
    }

    @Test
    @DisplayName("sem JWT → 401")
    void semAutenticacao() throws Exception {
        mockMvc.perform(post(rota()).with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("inscrito inexistente → 404")
    void inscritoInexistente() throws Exception {
        when(foundingInviteService.invite(any(), any())).thenThrow(new DomainNotFoundException("não encontrado"));

        mockMvc.perform(post(rota()).with(csrf()).with(jwtCom("ADMIN", "admin-sub")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("perfil ATLETA ou e-mail longo → 422")
    void regraDeDominio() throws Exception {
        when(foundingInviteService.invite(any(), any())).thenThrow(new DomainRuleViolationException("só TREINADOR"));

        mockMvc.perform(post(rota()).with(csrf()).with(jwtCom("ADMIN", "admin-sub")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("e-mail com conta ou convite já convertido → 409")
    void conflito() throws Exception {
        when(foundingInviteService.invite(any(), any())).thenThrow(new DomainConflictException("já possui conta"));

        mockMvc.perform(post(rota()).with(csrf()).with(jwtCom("ADMIN", "admin-sub")))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("SMTP recusou → 502, sem detalhe do transporte no corpo")
    void falhaDeEmail() throws Exception {
        when(foundingInviteService.invite(any(), any()))
                .thenThrow(new EmailDeliveryException("Falha ao enviar e-mail por SMTP (subject=x)", new RuntimeException("535 auth")));

        var corpo = mockMvc.perform(post(rota()).with(csrf()).with(jwtCom("ADMIN", "admin-sub")))
                .andExpect(status().isBadGateway())
                .andReturn().getResponse().getContentAsString();

        assertThat(corpo).doesNotContain("535").doesNotContain("SMTP");
    }
}
