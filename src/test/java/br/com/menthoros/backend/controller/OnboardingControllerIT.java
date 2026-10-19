package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.DispositivoMarca;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.enums.UserRole;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prova a persistencia da marca do relogio de ponta a ponta (POST do rascunho -> GET), que o
 * {@code OnboardingControllerTest} (slice com service e mapper mockados) nao alcanca. Autenticacao
 * por {@code jwt()} com subject UUID, nunca {@code @WithMockUser}.
 */
@AutoConfigureMockMvc
@DisplayName("Onboarding do atleta — marca do dispositivo persistida (contrato HTTP com seguranca real)")
class OnboardingControllerIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AssessoriaRepository assessoriaRepository;
    @Autowired private AtletaRepository atletaRepository;
    @Autowired private UsuarioRepository usuarioRepository;

    private UUID tenantId;
    private UUID atletaId;
    private UUID subAtleta;

    @BeforeEach
    void seed() {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria Onboarding IT");
        assessoria.setDominio("onb-marca-it-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);
        tenantId = assessoria.getId();

        Atleta atleta = new Atleta();
        atleta.setNome("Atleta Marca");
        atleta.setEmail("atleta-marca-" + UUID.randomUUID() + "@menthoros.test");
        atleta.setObjetivo("Correr uma maratona");
        atleta.setNivelExperiencia(NivelExperiencia.INTERMEDIARIO);
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setAssessoria(assessoria);
        atleta = atletaRepository.save(atleta);
        atletaId = atleta.getId();

        subAtleta = UUID.randomUUID();
        atleta.setUsuario(usuarioRepository.save(Usuario.builder()
                .id(subAtleta).keycloakId(subAtleta.toString())
                .assessoria(assessoria)
                .email(subAtleta + "@menthoros.test").nome("Usuario " + subAtleta)
                .role(UserRole.ATLETA).ativo(true)
                .build()));
        atletaRepository.save(atleta);
    }

    @ParameterizedTest
    @EnumSource(value = DispositivoMarca.class, names = {"AMAZFIT", "SAMSUNG"})
    @DisplayName("POST do rascunho com marca nova persiste e o GET devolve a mesma marca")
    void marcaNovaPersiste(DispositivoMarca marca) throws Exception {
        mockMvc.perform(post("/api/v1/atletas/{atletaId}/onboarding", atletaId)
                        .with(jwtAtleta())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"objetivo\":\"Correr uma maratona\",\"dispositivoMarca\":\"" + marca + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dispositivoMarca").value(marca.name()));

        mockMvc.perform(get("/api/v1/atletas/{atletaId}/onboarding", atletaId).with(jwtAtleta()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dispositivoMarca").value(marca.name()));
    }

    @Test
    @DisplayName("POST do rascunho sem marca devolve 400")
    void semMarcaDevolve400() throws Exception {
        mockMvc.perform(post("/api/v1/atletas/{atletaId}/onboarding", atletaId)
                        .with(jwtAtleta())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"objetivo\":\"Correr uma maratona\"}"))
                // Sem asserir o corpo: com spring.mvc.problemdetails.enabled=true o advice padrao do
                // Spring precede o GlobalExceptionHandler e o corpo nao traz "fields" para este erro.
                .andExpect(status().isBadRequest());
    }

    private RequestPostProcessor jwtAtleta() {
        return jwt()
                .authorities(new SimpleGrantedAuthority("ROLE_ATLETA"))
                .jwt(j -> j.subject(subAtleta.toString()).claim("tenant_id", tenantId.toString()));
    }
}
