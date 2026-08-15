package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.enums.UserRole;
import br.com.menthoros.backend.repository.AssessoriaLogoRepository;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O que os slices não conseguem provar.
 *
 * <p>{@code AssessoriaSettingsControllerTest} roda com {@code addFilters = false} — o padrão do
 * módulo — então lá nenhuma anotação {@code @PreAuthorize} é exercitada e o {@code TenantContext}
 * nunca é populado. Autorização por role, isolamento entre tenants e o gate de coerência só têm
 * valor com o contexto real e um JWT de verdade, que é o que esta classe faz.
 *
 * <p>Autenticação por {@code jwt()}, nunca {@code @WithMockUser}: o {@code JwtTenantFilter} só
 * popula o tenant quando o principal é um {@code Jwt}, e o subject precisa ser um UUID válido
 * porque {@code createNewUsuario} faz {@code UUID.fromString(keycloakId)}.
 */
@AutoConfigureMockMvc
@DisplayName("Configuração da assessoria — contrato HTTP com segurança real")
class AssessoriaSettingsControllerIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AssessoriaRepository assessoriaRepository;
    @Autowired private AssessoriaLogoRepository logoRepository;
    @Autowired private UsuarioRepository usuarioRepository;

    private Assessoria assessoriaA;
    private Assessoria assessoriaB;
    private UUID donoA;
    private UUID tecnicoA;
    private UUID donoB;

    @BeforeEach
    void seed() {
        assessoriaA = seedAssessoria("Corridas Serra");
        assessoriaB = seedAssessoria("Trilhas do Vale");

        donoA = seedUsuario(assessoriaA, true);
        tecnicoA = seedUsuario(assessoriaA, false);
        donoB = seedUsuario(assessoriaB, true);
    }

    @Nested
    @DisplayName("autorização")
    class Autorizacao {

        @Test
        @DisplayName("técnico não-dono lê a configuração")
        void tecnicoLe() throws Exception {
            mockMvc.perform(get("/api/v1/assessorias/me")
                            .with(jwtDe(tecnicoA, assessoriaA.getId(), "TECNICO")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nome").value("Corridas Serra"));
        }

        @Test
        @DisplayName("técnico não-dono NÃO altera a assessoria")
        void tecnicoNaoEscreve() throws Exception {
            mockMvc.perform(patch("/api/v1/assessorias/me")
                            .with(jwtDe(tecnicoA, assessoriaA.getId(), "TECNICO"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(patchBody("Nome Que Nao Deve Colar", versaoDe(assessoriaA))))
                    .andExpect(status().isForbidden());

            assertThat(recarregar(assessoriaA).getNome()).isEqualTo("Corridas Serra");
        }

        @Test
        @DisplayName("dono altera a assessoria")
        void donoEscreve() throws Exception {
            mockMvc.perform(patch("/api/v1/assessorias/me")
                            .with(jwtDe(donoA, assessoriaA.getId(), "PROPRIETARIO"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(patchBody("Corridas Serra Pro", versaoDe(assessoriaA))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nome").value("Corridas Serra Pro"));

            assertThat(recarregar(assessoriaA).getNome()).isEqualTo("Corridas Serra Pro");
        }

        @Test
        @DisplayName("técnico não-dono não remove a logo")
        void tecnicoNaoRemoveLogo() throws Exception {
            mockMvc.perform(delete("/api/v1/assessorias/me/logo")
                            .with(jwtDe(tecnicoA, assessoriaA.getId(), "TECNICO"))
                            .param("version", String.valueOf(versaoDe(assessoriaA))))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("isolamento entre tenants")
    class Isolamento {

        @Test
        @DisplayName("cada dono enxerga apenas a própria assessoria")
        void cadaDonoVeASua() throws Exception {
            mockMvc.perform(get("/api/v1/assessorias/me")
                            .with(jwtDe(donoA, assessoriaA.getId(), "PROPRIETARIO")))
                    .andExpect(jsonPath("$.nome").value("Corridas Serra"));

            mockMvc.perform(get("/api/v1/assessorias/me")
                            .with(jwtDe(donoB, assessoriaB.getId(), "PROPRIETARIO")))
                    .andExpect(jsonPath("$.nome").value("Trilhas do Vale"));
        }

        /**
         * O cenário do gate de coerência: token com {@code sub} do dono de A e {@code tenant_id} de
         * B — o que um usuário em duas Organizations do Keycloak pode produzir. A escrita tem de
         * ser recusada e B tem de permanecer intacta.
         */
        @Test
        @DisplayName("sub de um tenant com tenant_id de outro não escreve em nenhum dos dois")
        void tenantDivergenteNaoEscreve() throws Exception {
            mockMvc.perform(patch("/api/v1/assessorias/me")
                            .with(jwtDe(donoA, assessoriaB.getId(), "PROPRIETARIO"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(patchBody("Invasao", versaoDe(assessoriaB))))
                    .andExpect(status().isForbidden());

            assertThat(recarregar(assessoriaB).getNome()).isEqualTo("Trilhas do Vale");
            assertThat(recarregar(assessoriaA).getNome()).isEqualTo("Corridas Serra");
        }
    }

    @Nested
    @DisplayName("concorrência")
    class Concorrencia {

        @Test
        @DisplayName("segunda escrita com versão obsoleta recebe 409")
        void patchConcorrente() throws Exception {
            long versaoInicial = versaoDe(assessoriaA);

            mockMvc.perform(patch("/api/v1/assessorias/me")
                            .with(jwtDe(donoA, assessoriaA.getId(), "PROPRIETARIO"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(patchBody("Primeira Aba", versaoInicial)))
                    .andExpect(status().isOk());

            mockMvc.perform(patch("/api/v1/assessorias/me")
                            .with(jwtDe(donoA, assessoriaA.getId(), "PROPRIETARIO"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(patchBody("Segunda Aba", versaoInicial)))
                    .andExpect(status().isConflict());

            assertThat(recarregar(assessoriaA).getNome()).isEqualTo("Primeira Aba");
        }

        /**
         * O defeito que a primeira redação do design deixou passar: sem versão no DELETE, esta aba
         * apagaria a logo que a outra acabou de enviar, sem conflito e com perda de dado.
         */
        @Test
        @DisplayName("upload e depois delete com versão velha: 409 e a logo continua servível")
        void uploadDepoisDeleteObsoleto() throws Exception {
            long versaoInicial = versaoDe(assessoriaA);

            mockMvc.perform(multipart("/api/v1/assessorias/me/logo")
                            .file(new MockMultipartFile("arquivo", "logo.png", "image/png", png(32, 32)))
                            .param("version", String.valueOf(versaoInicial))
                            .with(jwtDe(donoA, assessoriaA.getId(), "PROPRIETARIO")))
                    .andExpect(status().isOk());

            mockMvc.perform(delete("/api/v1/assessorias/me/logo")
                            .param("version", String.valueOf(versaoInicial))
                            .with(jwtDe(donoA, assessoriaA.getId(), "PROPRIETARIO")))
                    .andExpect(status().isConflict());

            assertThat(logoRepository.existsByAssessoriaId(assessoriaA.getId())).isTrue();
        }

        @Test
        @DisplayName("delete e depois upload com versão velha: 409 e nada é gravado")
        void deleteDepoisUploadObsoleto() throws Exception {
            long versaoInicial = versaoDe(assessoriaA);

            mockMvc.perform(multipart("/api/v1/assessorias/me/logo")
                            .file(new MockMultipartFile("arquivo", "logo.png", "image/png", png(32, 32)))
                            .param("version", String.valueOf(versaoInicial))
                            .with(jwtDe(donoA, assessoriaA.getId(), "PROPRIETARIO")))
                    .andExpect(status().isOk());

            long aposUpload = versaoDe(assessoriaA);

            mockMvc.perform(delete("/api/v1/assessorias/me/logo")
                            .param("version", String.valueOf(aposUpload))
                            .with(jwtDe(donoA, assessoriaA.getId(), "PROPRIETARIO")))
                    .andExpect(status().isNoContent());

            mockMvc.perform(multipart("/api/v1/assessorias/me/logo")
                            .file(new MockMultipartFile("arquivo", "logo.png", "image/png", png(48, 48)))
                            .param("version", String.valueOf(aposUpload))
                            .with(jwtDe(donoA, assessoriaA.getId(), "PROPRIETARIO")))
                    .andExpect(status().isConflict());

            assertThat(logoRepository.existsByAssessoriaId(assessoriaA.getId())).isFalse();
        }
    }

    @Nested
    @DisplayName("ciclo da logo")
    class CicloDaLogo {

        @Test
        @DisplayName("upload, servir com ETag e 304 na segunda leitura")
        void uploadEDepois304() throws Exception {
            mockMvc.perform(multipart("/api/v1/assessorias/me/logo")
                            .file(new MockMultipartFile("arquivo", "logo.png", "image/png", png(64, 64)))
                            .param("version", String.valueOf(versaoDe(assessoriaA)))
                            .with(jwtDe(donoA, assessoriaA.getId(), "PROPRIETARIO")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.temLogo").value(true))
                    .andExpect(jsonPath("$.logoUrl").value("/api/v1/assessorias/me/logo"));

            MvcResult primeira = mockMvc.perform(get("/api/v1/assessorias/me/logo")
                            .with(jwtDe(donoA, assessoriaA.getId(), "PROPRIETARIO")))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"))
                    .andReturn();

            String etag = primeira.getResponse().getHeader(HttpHeaders.ETAG);
            assertThat(etag).isNotBlank();
            assertThat(primeira.getResponse().getContentAsByteArray()).isNotEmpty();

            mockMvc.perform(get("/api/v1/assessorias/me/logo")
                            .header(HttpHeaders.IF_NONE_MATCH, etag)
                            .with(jwtDe(donoA, assessoriaA.getId(), "PROPRIETARIO")))
                    .andExpect(status().isNotModified());
        }

        @Test
        @DisplayName("assessoria sem logo responde 404")
        void semLogo() throws Exception {
            mockMvc.perform(get("/api/v1/assessorias/me/logo")
                            .with(jwtDe(donoA, assessoriaA.getId(), "PROPRIETARIO")))
                    .andExpect(status().isNotFound());
        }

        /**
         * Extensão e {@code Content-Type} dizem PNG; o conteúdo é texto. A rejeição prova que a
         * validação decodifica em vez de acreditar no cliente — e que nada foi gravado.
         */
        @Test
        @DisplayName("arquivo falso com nome de imagem é recusado e não grava nada")
        void arquivoFalso() throws Exception {
            mockMvc.perform(multipart("/api/v1/assessorias/me/logo")
                            .file(new MockMultipartFile("arquivo", "logo.png", "image/png",
                                    "isto nao e uma imagem".getBytes(StandardCharsets.UTF_8)))
                            .param("version", String.valueOf(versaoDe(assessoriaA)))
                            .with(jwtDe(donoA, assessoriaA.getId(), "PROPRIETARIO")))
                    .andExpect(status().isUnprocessableEntity());

            assertThat(logoRepository.existsByAssessoriaId(assessoriaA.getId())).isFalse();
        }

        @Test
        @DisplayName("upload recusado não consome a versão da assessoria")
        void uploadRecusadoNaoConsomeVersao() throws Exception {
            long antes = versaoDe(assessoriaA);

            mockMvc.perform(multipart("/api/v1/assessorias/me/logo")
                            .file(new MockMultipartFile("arquivo", "logo.png", "image/png",
                                    "texto".getBytes(StandardCharsets.UTF_8)))
                            .param("version", String.valueOf(antes))
                            .with(jwtDe(donoA, assessoriaA.getId(), "PROPRIETARIO")))
                    .andExpect(status().isUnprocessableEntity());

            assertThat(versaoDe(assessoriaA)).isEqualTo(antes);
        }
    }

    @Nested
    @DisplayName("contrato do PATCH")
    class ContratoDoPatch {

        @Test
        @DisplayName("cor no payload é recusada mesmo vinda do dono")
        void corRecusada() throws Exception {
            mockMvc.perform(patch("/api/v1/assessorias/me")
                            .with(jwtDe(donoA, assessoriaA.getId(), "PROPRIETARIO"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"nome":"Corridas Serra","version":%d,"corPrimaria":"#FF6B35"}
                                    """.formatted(versaoDe(assessoriaA))))
                    .andExpect(status().isBadRequest());

            assertThat(recarregar(assessoriaA).getCorPrimaria()).isNotEqualTo("#FF6B35");
        }

        @Test
        @DisplayName("o dono aparece na contagem de técnicos do plano")
        void donoContaComoTecnico() throws Exception {
            mockMvc.perform(get("/api/v1/assessorias/me")
                            .with(jwtDe(donoA, assessoriaA.getId(), "PROPRIETARIO")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.uso.tecnicos").value(2));
        }
    }

    private RequestPostProcessor jwtDe(UUID subject, UUID tenantId, String papel) {
        return jwt()
                .authorities(new SimpleGrantedAuthority("ROLE_" + papel))
                .jwt(j -> j.subject(subject.toString()).claim("tenant_id", tenantId.toString()));
    }

    private String patchBody(String nome, long version) {
        return """
                {"nome":"%s","version":%d}
                """.formatted(nome, version);
    }

    private long versaoDe(Assessoria assessoria) {
        return recarregar(assessoria).getVersion();
    }

    private Assessoria recarregar(Assessoria assessoria) {
        return assessoriaRepository.findById(assessoria.getId()).orElseThrow();
    }

    private Assessoria seedAssessoria(String nome) {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome(nome);
        assessoria.setDominio("it-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria.setMaxAtletas(10);
        assessoria.setMaxTecnicos(2);
        return assessoriaRepository.save(assessoria);
    }

    private UUID seedUsuario(Assessoria assessoria, boolean owner) {
        UUID id = UUID.randomUUID();
        usuarioRepository.save(Usuario.builder()
                .id(id)
                .keycloakId(id.toString())
                .nome(owner ? "Dono" : "Tecnico")
                .email("usuario-" + id + "@exemplo.com")
                .role(UserRole.TECNICO)
                .owner(owner)
                .ativo(true)
                .assessoria(assessoria)
                .build());
        return id;
    }

    private byte[] png(int largura, int altura) throws IOException {
        BufferedImage imagem = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        ImageIO.write(imagem, "png", saida);
        return saida.toByteArray();
    }
}
