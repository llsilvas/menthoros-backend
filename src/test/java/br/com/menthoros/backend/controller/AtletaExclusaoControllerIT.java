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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exclusão de atleta — autorização.
 *
 * <p>O {@code DELETE} exigia {@code ADMIN} (administrador de plataforma), então o <b>dono da
 * assessoria não conseguia remover um atleta que ele mesmo cadastrou por engano</b>. Isso apareceu
 * no teste manual do wizard de boas-vindas, que convida a cadastrar um atleta no primeiro minuto de
 * uso — justamente quando o coach está aprendendo a interface e mais propenso a errar.
 *
 * <p>A operação é <b>soft delete tenant-scoped</b> (marca {@code INATIVO}), reversível e sem
 * alcance fora do tenant — por isso ampliar para o dono é seguro. Técnico contratado continua de
 * fora: inativar atleta é destrutivo para quem treina, e a distinção dono/contratado existe para
 * decisões assim.
 */
@AutoConfigureMockMvc
@DisplayName("Exclusão de atleta — quem pode")
class AtletaExclusaoControllerIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AssessoriaRepository assessoriaRepository;
    @Autowired private AtletaRepository atletaRepository;
    @Autowired private UsuarioRepository usuarioRepository;

    private Assessoria assessoria;
    private UUID dono;
    private UUID tecnico;
    private UUID atletaId;

    @BeforeEach
    void seed() {
        assessoria = seedAssessoria();
        dono = seedUsuario(assessoria, true);
        tecnico = seedUsuario(assessoria, false);
        atletaId = seedAtleta(assessoria);
    }

    @Test
    @DisplayName("o dono inativa o atleta da própria assessoria")
    void donoInativa() throws Exception {
        mockMvc.perform(delete("/api/v1/atletas/{id}", atletaId)
                        .with(jwtDe(dono, assessoria.getId(), "PROPRIETARIO")))
                .andExpect(status().isNoContent());

        assertThat(atletaRepository.findById(atletaId).orElseThrow().getAtivo())
                .isEqualTo(AtletaStatus.INATIVO);
    }

    @Test
    @DisplayName("técnico contratado não inativa")
    void tecnicoNaoInativa() throws Exception {
        mockMvc.perform(delete("/api/v1/atletas/{id}", atletaId)
                        .with(jwtDe(tecnico, assessoria.getId(), "TECNICO")))
                .andExpect(status().isForbidden());

        assertThat(atletaRepository.findById(atletaId).orElseThrow().getAtivo())
                .as("o atleta não pode ter sido tocado")
                .isEqualTo(AtletaStatus.ATIVO);
    }

    /**
     * O dono de A não alcança o atleta de B mesmo tendo a role — a consulta é tenant-scoped, e o
     * `403`/`404` aqui é a diferença entre uma permissão e uma permissão irrestrita.
     */
    @Test
    @DisplayName("dono de outra assessoria não alcança o atleta")
    void donoDeOutraAssessoria() throws Exception {
        Assessoria outra = seedAssessoria();
        UUID donoDaOutra = seedUsuario(outra, true);

        mockMvc.perform(delete("/api/v1/atletas/{id}", atletaId)
                        .with(jwtDe(donoDaOutra, outra.getId(), "PROPRIETARIO")))
                .andExpect(status().isNotFound());

        assertThat(atletaRepository.findById(atletaId).orElseThrow().getAtivo())
                .isEqualTo(AtletaStatus.ATIVO);
    }

    private RequestPostProcessor jwtDe(UUID subject, UUID tenantId, String papel) {
        return jwt()
                .authorities(new SimpleGrantedAuthority("ROLE_" + papel))
                .jwt(j -> j.subject(subject.toString()).claim("tenant_id", tenantId.toString()));
    }

    private Assessoria seedAssessoria() {
        Assessoria a = new Assessoria();
        a.setNome("Assessoria Exclusão");
        a.setDominio("excl-" + UUID.randomUUID());
        a.setPlano(PlanoAssessoria.BASIC);
        return assessoriaRepository.save(a);
    }

    private UUID seedUsuario(Assessoria a, boolean owner) {
        UUID id = UUID.randomUUID();
        usuarioRepository.save(Usuario.builder()
                .id(id).keycloakId(id.toString())
                .nome(owner ? "Dono" : "Tecnico")
                .email("excl-" + id + "@exemplo.com")
                .role(UserRole.TECNICO).owner(owner).ativo(true)
                .assessoria(a)
                .build());
        return id;
    }

    private UUID seedAtleta(Assessoria a) {
        Atleta atleta = new Atleta();
        atleta.setNome("Atleta Duplicado");
        // NOT NULL no schema, ainda que o Bean Validation do DTO o trate como obrigatório só na API.
        atleta.setNivelExperiencia(NivelExperiencia.INICIANTE);
        atleta.setEmail("dup-" + UUID.randomUUID() + "@exemplo.com");
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setAssessoria(a);
        return atletaRepository.save(atleta).getId();
    }
}
