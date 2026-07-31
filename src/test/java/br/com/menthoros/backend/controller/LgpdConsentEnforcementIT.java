package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.enums.UserRole;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.UsuarioLgpdConsentRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Enforcement de consentimento no contexto real, com a flag em {@code on}.
 *
 * <p>Os testes de unidade do interceptor provam a lógica das guardas; este prova que ela está de
 * fato ligada na cadeia MVC — que o interceptor foi registrado, que o {@code JwtTenantFilter}
 * deposita o usuário onde o interceptor lê, e que o handler traduz a exceção no status e no código
 * combinados. Nenhuma dessas três coisas aparece num teste de unidade.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.lgpd.consent-enforcement=on",
        "app.lgpd.policy-version=2026-06-30",
        "app.lgpd.terms-version=2026-06-30"
})
class LgpdConsentEnforcementIT extends AbstractIntegrationTest {

    private static final String POLICY = "2026-06-30";
    private static final String TERMS = "2026-06-30";

    @Autowired private MockMvc mockMvc;
    @Autowired private AssessoriaRepository assessoriaRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private UsuarioLgpdConsentRepository consentRepository;

    private UUID tenantId;
    private String keycloakId;

    @BeforeEach
    void setUp() {
        consentRepository.deleteAll();
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria Enforcement");
        assessoria.setDominio("enforce-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);
        tenantId = assessoria.getId();

        UUID id = UUID.randomUUID();
        keycloakId = id.toString();
        usuarioRepository.save(Usuario.builder()
                .id(id)
                .keycloakId(keycloakId)
                .nome("Coach")
                .email("coach-" + id + "@exemplo.com")
                .role(UserRole.TECNICO)
                .ativo(true)
                .assessoria(assessoria)
                .build());
    }

    @Test
    @DisplayName("coach sem consentimento é bloqueado na escrita e liberado após aceitar")
    void bloqueiaAntesELiberaDepois() throws Exception {
        // Antes do aceite: escrita bloqueada com o código que o frontend usa para abrir o modal.
        mockMvc.perform(post("/api/v1/atletas/{atletaId}/provas", UUID.randomUUID())
                        .with(coachJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LGPD_CONSENT_REQUIRED"));

        // O próprio endpoint de consentimento nunca é bloqueado — senão seria deadlock.
        mockMvc.perform(post("/api/v1/users/me/consent")
                        .with(coachJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"termsAccepted":true,"privacyPolicyAccepted":true,
                                 "policyVersion":"%s","termsVersion":"%s"}
                                """.formatted(POLICY, TERMS)))
                .andExpect(status().isOk());

        assertThat(consentRepository.findAll()).hasSize(1);

        // Depois do aceite a mesma escrita não é mais barrada pelo gate. O status resultante é o do
        // próprio endpoint (400 pelo corpo vazio) — o que importa é não ser mais 403.
        mockMvc.perform(post("/api/v1/atletas/{atletaId}/provas", UUID.randomUUID())
                        .with(coachJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(not403());
    }

    @Test
    @DisplayName("reenviar o mesmo aceite é idempotente — não cria segunda linha")
    void reenvioIdempotente() throws Exception {
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/users/me/consent")
                            .with(coachJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"termsAccepted":true,"privacyPolicyAccepted":true,
                                     "policyVersion":"%s","termsVersion":"%s"}
                                    """.formatted(POLICY, TERMS)))
                    .andExpect(status().isOk());
        }

        assertThat(consentRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("versão defasada é recusada com 409 CONSENT_VERSION_STALE e não grava")
    void versaoDefasada() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/consent")
                        .with(coachJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"termsAccepted":true,"privacyPolicyAccepted":true,
                                 "policyVersion":"2020-01-01","termsVersion":"2020-01-01"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONSENT_VERSION_STALE"));

        assertThat(consentRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("webhook público segue funcionando com a flag em on")
    void webhookPublicoNaoAfetado() throws Exception {
        // Sem JWT: guarda 1 deixa passar. O status vem do próprio endpoint, nunca 403 do gate.
        mockMvc.perform(post("/api/v1/strava/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(not403());
    }

    private org.springframework.test.web.servlet.ResultMatcher not403() {
        return result -> assertThat(result.getResponse().getStatus())
                .as("o gate de consentimento não deveria ter bloqueado esta requisição")
                .isNotEqualTo(403);
    }

    private RequestPostProcessor coachJwt() {
        return jwt()
                .authorities(new SimpleGrantedAuthority("ROLE_TECNICO"))
                .jwt(j -> j.claim("tenant_id", tenantId.toString()).subject(keycloakId));
    }
}
