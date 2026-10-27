package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.enums.UserRole;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@DisplayName("GET /api/v1/coach/atletas/{id}/zonas — tenant e papel")
class CoachAthleteZonesControllerIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AssessoriaRepository assessoriaRepository;
    @Autowired private AtletaRepository atletaRepository;
    @Autowired private UsuarioRepository usuarioRepository;

    private UUID tenantAId;
    private UUID tenantBId;
    private UUID atletaAId;
    private UUID subTecnicoA;
    private UUID subTecnicoB;
    private UUID subAtletaA;

    @BeforeEach
    void setUp() {
        Assessoria a = criarAssessoria("A");
        Assessoria b = criarAssessoria("B");
        tenantAId = a.getId();
        tenantBId = b.getId();

        Atleta atleta = new Atleta();
        atleta.setNome("Atleta Zonas");
        atleta.setEmail("zonas-" + UUID.randomUUID() + "@menthoros.test");
        atleta.setObjetivo("Correr 10 km");
        atleta.setNivelExperiencia(NivelExperiencia.INTERMEDIARIO);
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setAssessoria(a);
        atleta.setPaceLimiar(new BigDecimal("5.00"));
        atleta.setFcLimiar(168);
        atleta.setFcMaxima(185);
        atletaAId = atletaRepository.save(atleta).getId();

        subTecnicoA = UUID.randomUUID();
        subTecnicoB = UUID.randomUUID();
        subAtletaA = UUID.randomUUID();
        criarUsuario(subTecnicoA, a, UserRole.TECNICO);
        criarUsuario(subTecnicoB, b, UserRole.TECNICO);
        criarUsuario(subAtletaA, a, UserRole.ATLETA);
    }

    @Test
    @DisplayName("TECNICO do tenant recebe as seis zonas com valores do atleta -> 200")
    void tecnicoDoTenant() throws Exception {
        mockMvc.perform(get("/api/v1/coach/atletas/{id}/zonas", atletaAId)
                        .with(jwtDe(subTecnicoA, tenantAId, "TECNICO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zonas.length()").value(6))
                .andExpect(jsonPath("$.zonas[1].pace.minSegKm").value(315))
                .andExpect(jsonPath("$.zonas[1].fc.min").value(143))
                .andExpect(jsonPath("$.zonas[5].motivoFc").value("FC_NAO_ESTABILIZA"))
                .andExpect(jsonPath("$.fcMaxima.bpm").value(185));
    }

    @Test
    @DisplayName("TECNICO de outro tenant -> 404, não 403")
    void outroTenant() throws Exception {
        mockMvc.perform(get("/api/v1/coach/atletas/{id}/zonas", atletaAId)
                        .with(jwtDe(subTecnicoB, tenantBId, "TECNICO")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("ATLETA não usa o endpoint do coach -> 403")
    void atletaRecebe403() throws Exception {
        mockMvc.perform(get("/api/v1/coach/atletas/{id}/zonas", atletaAId)
                        .with(jwtDe(subAtletaA, tenantAId, "ATLETA")))
                .andExpect(status().isForbidden());
    }

    private Assessoria criarAssessoria(String sufixo) {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria " + sufixo);
        assessoria.setDominio("zonas-" + sufixo.toLowerCase() + "-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        return assessoriaRepository.save(assessoria);
    }

    private void criarUsuario(UUID subject, Assessoria assessoria, UserRole role) {
        usuarioRepository.save(Usuario.builder()
                .id(subject)
                .keycloakId(subject.toString())
                .assessoria(assessoria)
                .email(subject + "@menthoros.test")
                .nome("Usuario " + subject)
                .role(role)
                .ativo(true)
                .build());
    }

    private RequestPostProcessor jwtDe(UUID subject, UUID tenantId, String papel) {
        return jwt()
                .authorities(new SimpleGrantedAuthority("ROLE_" + papel))
                .jwt(j -> j.subject(subject.toString()).claim("tenant_id", tenantId.toString()));
    }
}
