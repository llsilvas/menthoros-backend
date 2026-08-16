package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.AssessoriaLogo;
import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.enums.UserRole;
import br.com.menthoros.backend.repository.AssessoriaLogoRepository;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Logo da assessoria no {@code GET /api/v1/users/me} — contrato com dado real no banco.
 *
 * <p><b>É este teste que trava a armadilha da change.</b> O teste de unidade do mapper recebe
 * {@code temLogo} já resolvido, então passaria mesmo que a service consultasse a fonte errada. A
 * entidade {@code Assessoria} tem um campo {@code logoUrl} legado, {@code NULL} desde que a logo
 * virou BLOB em {@code tb_assessoria_logo}: mapear aquele campo compila, passa nos unitários e
 * devolve {@code null} para todo mundo — exatamente o bug que a change existe para corrigir.
 *
 * <p>Aqui a logo é gravada como BLOB de verdade, e o payload tem de refleti-la.
 */
@AutoConfigureMockMvc
@DisplayName("GET /users/me — logo da assessoria")
class UsuarioMeLogoIT extends AbstractIntegrationTest {

    private static final String ROTA_DO_LOGO = "/api/v1/assessorias/me/logo";

    @Autowired private MockMvc mockMvc;
    @Autowired private AssessoriaRepository assessoriaRepository;
    @Autowired private AssessoriaLogoRepository logoRepository;
    @Autowired private UsuarioRepository usuarioRepository;

    @Test
    @DisplayName("com logo gravada, o me devolve a rota e a versão")
    void comLogo() throws Exception {
        Assessoria assessoria = seedAssessoria();
        UUID coach = seedCoach(assessoria);
        seedLogo(assessoria);

        mockMvc.perform(get("/api/v1/users/me").with(jwtDe(coach, assessoria.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assessoria.temLogo").value(true))
                .andExpect(jsonPath("$.assessoria.logoUrl").value(ROTA_DO_LOGO))
                .andExpect(jsonPath("$.assessoria.version").isNumber());
    }

    @Test
    @DisplayName("sem logo, devolve temLogo=false e logoUrl nulo")
    void semLogo() throws Exception {
        Assessoria assessoria = seedAssessoria();
        UUID coach = seedCoach(assessoria);

        mockMvc.perform(get("/api/v1/users/me").with(jwtDe(coach, assessoria.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assessoria.temLogo").value(false))
                .andExpect(jsonPath("$.assessoria.logoUrl").doesNotExist());
    }

    /** O contrato anterior devolvia os campos antigos e não pode ter regredido. */
    @Test
    @DisplayName("os campos que já existiam continuam no payload")
    void naoRegridiu() throws Exception {
        Assessoria assessoria = seedAssessoria();
        UUID coach = seedCoach(assessoria);

        mockMvc.perform(get("/api/v1/users/me").with(jwtDe(coach, assessoria.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assessoria.id").value(assessoria.getId().toString()))
                .andExpect(jsonPath("$.assessoria.nome").value(assessoria.getNome()))
                .andExpect(jsonPath("$.email").exists());
    }

    private RequestPostProcessor jwtDe(UUID subject, UUID tenantId) {
        return jwt()
                .authorities(new SimpleGrantedAuthority("ROLE_TECNICO"))
                .jwt(j -> j.subject(subject.toString()).claim("tenant_id", tenantId.toString()));
    }

    private Assessoria seedAssessoria() {
        Assessoria a = new Assessoria();
        a.setNome("Assessoria Logo");
        a.setDominio("logo-" + UUID.randomUUID());
        a.setPlano(PlanoAssessoria.BASIC);
        return assessoriaRepository.save(a);
    }

    private UUID seedCoach(Assessoria assessoria) {
        UUID id = UUID.randomUUID();
        usuarioRepository.save(Usuario.builder()
                .id(id).keycloakId(id.toString())
                .nome("Coach Logo")
                .email("logo-" + id + "@exemplo.com")
                .role(UserRole.TECNICO).ativo(true)
                .assessoria(assessoria)
                .build());
        return id;
    }

    private void seedLogo(Assessoria assessoria) {
        AssessoriaLogo logo = new AssessoriaLogo();
        logo.setAssessoriaId(assessoria.getId());
        logo.setContentType("image/png");
        logo.setContent(new byte[] { 1, 2, 3, 4 });
        logo.setSizeBytes(4);
        logo.setEtag("etag-teste");
        logoRepository.save(logo);
    }
}
