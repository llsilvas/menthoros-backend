package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.enums.UserRole;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import br.com.menthoros.backend.repository.UsuarioTrajetoShareConsentRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ciclo real conceder → revogar → conceder, com banco: prova que o histórico é append-only (cada
 * evento vira linha), que o estado é o último evento e que o consentimento é por tenant.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.lgpd.policy-version=2026-11-09")
class TrajetoShareConsentIT extends AbstractIntegrationTest {

    private static final String URL = "/api/v1/atletas/me/consentimentos/trajeto-compartilhamento";
    private static final String VERSAO = "{\"policyVersion\":\"2026-11-09\"}";

    @Autowired private MockMvc mockMvc;
    @Autowired private AssessoriaRepository assessoriaRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private UsuarioTrajetoShareConsentRepository consentRepository;

    private Usuario atletaA;
    private Assessoria assessoriaA;
    private Assessoria assessoriaB;

    @BeforeEach
    void setUp() {
        assessoriaA = assessoria("A");
        assessoriaB = assessoria("B");
        atletaA = usuario(assessoriaA);
    }

    @Test
    @DisplayName("conceder → revogar → conceder grava três eventos e o estado segue o último (CA3, CA7)")
    void cicloCompleto() throws Exception {
        mockMvc.perform(get(URL).with(atletaJwt(atletaA, assessoriaA)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.granted").value(false));

        mockMvc.perform(post(URL).with(atletaJwt(atletaA, assessoriaA)).contentType(MediaType.APPLICATION_JSON).content(VERSAO))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.granted").value(true));
        // Repetido: idempotente, não grava de novo (CA9).
        mockMvc.perform(post(URL).with(atletaJwt(atletaA, assessoriaA)).contentType(MediaType.APPLICATION_JSON).content(VERSAO))
                .andExpect(status().isCreated());

        mockMvc.perform(delete(URL).with(atletaJwt(atletaA, assessoriaA)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.granted").value(false));

        mockMvc.perform(post(URL).with(atletaJwt(atletaA, assessoriaA)).contentType(MediaType.APPLICATION_JSON).content(VERSAO))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.granted").value(true));

        assertThat(consentRepository.countByUsuario_IdAndTenantId(atletaA.getId(), assessoriaA.getId())).isEqualTo(3);
    }

    @Test
    @DisplayName("revogar logo após conceder: o último evento é a revogação, mesmo no mesmo instante")
    void ordemPelaSequenciaDoBanco() throws Exception {
        mockMvc.perform(post(URL).with(atletaJwt(atletaA, assessoriaA)).contentType(MediaType.APPLICATION_JSON).content(VERSAO))
                .andExpect(status().isCreated());
        mockMvc.perform(delete(URL).with(atletaJwt(atletaA, assessoriaA))).andExpect(status().isOk());

        mockMvc.perform(get(URL).with(atletaJwt(atletaA, assessoriaA)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.granted").value(false));
    }

    @Test
    @DisplayName("versão defasada: 409 CONSENT_VERSION_STALE e nenhuma linha (CA6)")
    void versaoDefasada() throws Exception {
        mockMvc.perform(post(URL).with(atletaJwt(atletaA, assessoriaA)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"policyVersion\":\"2026-10-26\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONSENT_VERSION_STALE"));

        assertThat(consentRepository.countByUsuario_IdAndTenantId(atletaA.getId(), assessoriaA.getId())).isZero();
    }

    @Test
    @DisplayName("consentimento de um tenant não vale em outro (CA8)")
    void isolamentoPorTenant() throws Exception {
        mockMvc.perform(post(URL).with(atletaJwt(atletaA, assessoriaA)).contentType(MediaType.APPLICATION_JSON).content(VERSAO))
                .andExpect(status().isCreated());

        Usuario atletaB = usuario(assessoriaB);
        mockMvc.perform(get(URL).with(atletaJwt(atletaB, assessoriaB)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.granted").value(false));
    }

    private Assessoria assessoria(String sufixo) {
        Assessoria a = new Assessoria();
        a.setNome("Assessoria " + sufixo);
        a.setDominio("trajeto-" + sufixo.toLowerCase() + "-" + UUID.randomUUID());
        a.setPlano(PlanoAssessoria.BASIC);
        return assessoriaRepository.save(a);
    }

    private Usuario usuario(Assessoria assessoria) {
        UUID id = UUID.randomUUID();
        return usuarioRepository.save(Usuario.builder()
                .id(id)
                .keycloakId(id.toString())
                .nome("Atleta")
                .email("atleta-" + id + "@exemplo.com")
                .role(UserRole.ATLETA)
                .ativo(true)
                .assessoria(assessoria)
                .build());
    }

    private RequestPostProcessor atletaJwt(Usuario usuario, Assessoria assessoria) {
        return jwt()
                .authorities(new SimpleGrantedAuthority("ROLE_ATLETA"))
                .jwt(j -> j.claim("tenant_id", assessoria.getId().toString()).subject(usuario.getKeycloakId()));
    }
}
