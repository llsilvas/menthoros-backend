package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.FoundingInvite;
import br.com.menthoros.backend.entity.Waitlist;
import br.com.menthoros.backend.enums.PerfilWaitlist;
import br.com.menthoros.backend.repository.FoundingInviteRepository;
import br.com.menthoros.backend.repository.WaitlistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test do endpoint administrativo {@code GET /api/admin/waitlist/funnel} — contexto
 * real (segurança, Postgres via Testcontainers).
 */
@AutoConfigureMockMvc
@DisplayName("GET /api/admin/waitlist/funnel")
class WaitlistFunnelControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WaitlistRepository waitlistRepository;

    @Autowired
    private FoundingInviteRepository foundingInviteRepository;

    @BeforeEach
    void limpar() {
        foundingInviteRepository.deleteAll();
        waitlistRepository.deleteAll();
    }

    private Waitlist salvarLead(PerfilWaitlist perfil, String utmSource, String utmContent) {
        String email = "lead-" + UUID.randomUUID() + "@exemplo.com";
        return waitlistRepository.save(Waitlist.builder()
                .nome("Lead")
                .email(email)
                .emailNormalized(email)
                .perfil(perfil)
                .aceiteLgpd(true)
                .utmSource(utmSource)
                .utmContent(utmContent)
                .build());
    }

    private void salvarConvite(UUID waitlistId, boolean convertido) {
        foundingInviteRepository.save(FoundingInvite.builder()
                .waitlistId(waitlistId)
                .tokenHash("hash-" + UUID.randomUUID())
                .email("convidado-" + UUID.randomUUID() + "@exemplo.com")
                .expiresAt(OffsetDateTime.now().plusDays(5))
                .invitedBy("admin")
                .convertedAt(convertido ? OffsetDateTime.now() : null)
                .build());
    }

    @Test
    @DisplayName("ADMIN agrega por UTM, inclusive leads sem invited/active")
    void agregaCorretamente() throws Exception {
        Waitlist bio1 = salvarLead(PerfilWaitlist.TREINADOR, "instagram", "bio-link");
        salvarLead(PerfilWaitlist.ATLETA, "instagram", "bio-link");
        salvarConvite(bio1.getId(), false);

        Waitlist post1 = salvarLead(PerfilWaitlist.TREINADOR, "instagram", "post-fixado");
        salvarConvite(post1.getId(), true);

        mockMvc.perform(get("/api/admin/waitlist/funnel")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("sem role ADMIN → 403")
    void semRoleAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/waitlist/funnel")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_TECNICO"))))
                .andExpect(status().isForbidden());
    }
}
