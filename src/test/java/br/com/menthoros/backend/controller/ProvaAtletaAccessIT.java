package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.DistanciaProva;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.enums.ProvaStatus;
import br.com.menthoros.backend.enums.TipoProva;
import br.com.menthoros.backend.enums.UserRole;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.ProvaRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Posse por {@code atletaId} (spec prova-crud, "Isolamento multi-tenancy"): dois atletas do MESMO
 * tenant — o dono recebe 200/201/204 em cada verbo, o outro recebe 404 sem distinção de "não
 * existe", e o TECNICO do tenant continua com acesso. Contexto completo de propósito: a posse
 * depende de {@code JwtTenantFilter} + {@code AuthenticatedAtletaResolver} + service reais.
 */
@AutoConfigureMockMvc
@DisplayName("Posse de prova por atletaId — dois atletas do mesmo tenant")
class ProvaAtletaAccessIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AssessoriaRepository assessoriaRepository;
    @Autowired private AtletaRepository atletaRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ProvaRepository provaRepository;

    private UUID tenantId;
    private UUID atletaDonoId;
    private UUID atletaOutroId;
    private UUID provaDoDonoId;
    private UUID subDono;
    private UUID subOutro;
    private UUID subTecnico;

    private static final String BODY = """
            {"nomeProva":"Maratona SP","dataProva":"%s","tipoProva":"MARATONA","distancia":"KM_42","provaAlvo":false}
            """;

    @BeforeEach
    void setUp() {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria Provas");
        assessoria.setDominio("provas-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);
        tenantId = assessoria.getId();

        subDono = UUID.randomUUID();
        subOutro = UUID.randomUUID();
        subTecnico = UUID.randomUUID();

        Atleta dono = criarAtleta(assessoria, criarUsuario(subDono, assessoria, UserRole.ATLETA));
        Atleta outro = criarAtleta(assessoria, criarUsuario(subOutro, assessoria, UserRole.ATLETA));
        criarUsuario(subTecnico, assessoria, UserRole.TECNICO);
        atletaDonoId = dono.getId();
        atletaOutroId = outro.getId();

        Prova prova = Prova.builder()
                .nomeProva("Meia do Rio")
                .dataProva(LocalDate.now().plusWeeks(20))
                .distancia(DistanciaProva.KM_21)
                .tipoProva(TipoProva.MEIA)
                .statusProva(ProvaStatus.PLANEJADA)
                .foiRealizada(false)
                .atleta(dono)
                .assessoria(assessoria)
                .build();
        provaDoDonoId = provaRepository.save(prova).getId();
    }

    @Nested
    @DisplayName("GET lista")
    class Listar {

        @Test
        @DisplayName("dono 200 com a prova; outro atleta 404; TECNICO 200")
        void posse() throws Exception {
            mockMvc.perform(get("/api/v1/atletas/{id}/provas", atletaDonoId).with(jwtDe(subDono, "ATLETA")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].nomeProva").value("Meia do Rio"));
            mockMvc.perform(get("/api/v1/atletas/{id}/provas", atletaDonoId).with(jwtDe(subOutro, "ATLETA")))
                    .andExpect(status().isNotFound());
            mockMvc.perform(get("/api/v1/atletas/{id}/provas", atletaDonoId).with(jwtDe(subTecnico, "TECNICO")))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("GET por id")
    class Buscar {

        @Test
        @DisplayName("dono 200; outro atleta 404; TECNICO 200")
        void posse() throws Exception {
            mockMvc.perform(get("/api/v1/atletas/{id}/provas/{p}", atletaDonoId, provaDoDonoId).with(jwtDe(subDono, "ATLETA")))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/v1/atletas/{id}/provas/{p}", atletaDonoId, provaDoDonoId).with(jwtDe(subOutro, "ATLETA")))
                    .andExpect(status().isNotFound());
            mockMvc.perform(get("/api/v1/atletas/{id}/provas/{p}", atletaDonoId, provaDoDonoId).with(jwtDe(subTecnico, "TECNICO")))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("POST")
    class Criar {

        @Test
        @DisplayName("dono 201 com derivados; outro atleta 404 e nada criado")
        void posse() throws Exception {
            String body = BODY.formatted(LocalDate.now().plusWeeks(8));

            mockMvc.perform(post("/api/v1/atletas/{id}/provas", atletaDonoId).with(jwtDe(subDono, "ATLETA"))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.semanasPreparacao").value(16))
                    .andExpect(jsonPath("$.preparacaoCurta").value(true))
                    .andExpect(jsonPath("$.distanciaKm").value(42.2));

            long antes = provaRepository.count();
            mockMvc.perform(post("/api/v1/atletas/{id}/provas", atletaDonoId).with(jwtDe(subOutro, "ATLETA"))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isNotFound());
            assertThat(provaRepository.count()).isEqualTo(antes);
        }

        @Test
        @DisplayName("atleta com data de hoje recebe 400 em dataProva")
        void dataDeHoje() throws Exception {
            mockMvc.perform(post("/api/v1/atletas/{id}/provas", atletaDonoId).with(jwtDe(subDono, "ATLETA"))
                            .contentType(MediaType.APPLICATION_JSON).content(BODY.formatted(LocalDate.now())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields.dataProva").exists());
        }
    }

    @Nested
    @DisplayName("PUT")
    class Atualizar {

        @Test
        @DisplayName("dono 200; outro atleta 404; TECNICO 200")
        void posse() throws Exception {
            String body = BODY.formatted(LocalDate.now().plusWeeks(20));

            mockMvc.perform(put("/api/v1/atletas/{id}/provas/{p}", atletaDonoId, provaDoDonoId).with(jwtDe(subDono, "ATLETA"))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nomeProva").value("Maratona SP"));
            mockMvc.perform(put("/api/v1/atletas/{id}/provas/{p}", atletaDonoId, provaDoDonoId).with(jwtDe(subOutro, "ATLETA"))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isNotFound());
            // O coach usa o DTO completo: status e resultado fazem parte do contrato dele.
            String bodyCoach = body.replace("\"provaAlvo\":false", "\"provaAlvo\":false,\"statusProva\":\"PLANEJADA\",\"foiRealizada\":false");
            mockMvc.perform(put("/api/v1/atletas/{id}/provas/{p}", atletaDonoId, provaDoDonoId).with(jwtDe(subTecnico, "TECNICO"))
                            .contentType(MediaType.APPLICATION_JSON).content(bodyCoach))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("mudança de data pelo dono fica pendente de ciência; PATCH ciente do TECNICO limpa; atleta não pode dar ciente")
        void cienciaDoCoach() throws Exception {
            String body = BODY.formatted(LocalDate.now().plusWeeks(21));
            mockMvc.perform(put("/api/v1/atletas/{id}/provas/{p}", atletaDonoId, provaDoDonoId).with(jwtDe(subDono, "ATLETA"))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.revisadaPeloCoach").value(false))
                    .andExpect(jsonPath("$.motivoRevisao").value("DATA_ALTERADA"));

            mockMvc.perform(get("/api/v1/atletas/{id}/provas/pendentes-revisao", atletaDonoId).with(jwtDe(subTecnico, "TECNICO")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(provaDoDonoId.toString()));

            mockMvc.perform(patch("/api/v1/atletas/{id}/provas/{p}/ciente", atletaDonoId, provaDoDonoId).with(jwtDe(subDono, "ATLETA")))
                    .andExpect(status().isForbidden());

            mockMvc.perform(patch("/api/v1/atletas/{id}/provas/{p}/ciente", atletaDonoId, provaDoDonoId).with(jwtDe(subTecnico, "TECNICO")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.revisadaPeloCoach").value(true))
                    .andExpect(jsonPath("$.motivoRevisao").doesNotExist());

            mockMvc.perform(get("/api/v1/atletas/{id}/provas/pendentes-revisao", atletaDonoId).with(jwtDe(subTecnico, "TECNICO")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isEmpty());
        }
    }

    @Nested
    @DisplayName("DELETE")
    class Remover {

        @Test
        @DisplayName("outro atleta 404; dono 204 cancela e a prova some da lista mas continua no banco")
        void posse() throws Exception {
            mockMvc.perform(delete("/api/v1/atletas/{id}/provas/{p}", atletaDonoId, provaDoDonoId).with(jwtDe(subOutro, "ATLETA")))
                    .andExpect(status().isNotFound());

            mockMvc.perform(delete("/api/v1/atletas/{id}/provas/{p}", atletaDonoId, provaDoDonoId).with(jwtDe(subDono, "ATLETA")))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get("/api/v1/atletas/{id}/provas", atletaDonoId).with(jwtDe(subDono, "ATLETA")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isEmpty());
            assertThat(provaRepository.findById(provaDoDonoId))
                    .get()
                    .extracting(Prova::getStatusProva)
                    .isEqualTo(ProvaStatus.CANCELADA);
        }
    }

    // ===== Helpers =====

    private Usuario criarUsuario(UUID subject, Assessoria assessoria, UserRole role) {
        Usuario usuario = Usuario.builder()
                .id(subject)
                .keycloakId(subject.toString())
                .assessoria(assessoria)
                .email(subject + "@menthoros.test")
                .nome("Usuario " + subject)
                .role(role)
                .ativo(true)
                .build();
        return usuarioRepository.save(usuario);
    }

    private Atleta criarAtleta(Assessoria assessoria, Usuario usuario) {
        Atleta atleta = new Atleta();
        atleta.setNome("Atleta " + usuario.getId());
        atleta.setEmail(usuario.getEmail());
        atleta.setObjetivo("Correr");
        atleta.setNivelExperiencia(NivelExperiencia.INTERMEDIARIO);
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setAssessoria(assessoria);
        atleta.setUsuario(usuario);
        return atletaRepository.save(atleta);
    }

    private RequestPostProcessor jwtDe(UUID subject, String papel) {
        return jwt()
                .authorities(new SimpleGrantedAuthority("ROLE_" + papel))
                .jwt(j -> j.subject(subject.toString()).claim("tenant_id", tenantId.toString()));
    }
}
