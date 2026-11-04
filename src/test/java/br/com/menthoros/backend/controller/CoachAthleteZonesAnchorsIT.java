package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.FonteLimiar;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.enums.UserRole;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PUT {@code .../zonas/ancoras} e POST {@code .../zonas/simulacao} contra PostgreSQL real: CHECKs de V6 e
 * V107, releitura em transação separada, isolamento entre tenants e concorrência entre eixos.
 *
 * <p>Autenticação por JWT (não {@code @WithMockUser}); subject UUID com {@code Usuario} existente.
 */
@AutoConfigureMockMvc
@DisplayName("Âncoras e simulação de zonas — integração")
class CoachAthleteZonesAnchorsIT extends AbstractIntegrationTest {

    private static final String URL = "/api/v1/coach/atletas/{id}/zonas";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AssessoriaRepository assessoriaRepository;
    @Autowired private AtletaRepository atletaRepository;
    @Autowired private UsuarioRepository usuarioRepository;

    private UUID tenantAId;
    private UUID tenantBId;
    private UUID atletaId;
    private UUID subTecnicoA;
    private UUID subTecnicoB;
    private UUID subAtletaA;
    private String dataTeste;

    @BeforeEach
    void setUp() {
        Assessoria a = criarAssessoria("A");
        Assessoria b = criarAssessoria("B");
        tenantAId = a.getId();
        tenantBId = b.getId();
        dataTeste = LocalDate.now().minusDays(10).toString();

        atletaId = criarAtleta(a, null, null, null);

        subTecnicoA = UUID.randomUUID();
        subTecnicoB = UUID.randomUUID();
        subAtletaA = UUID.randomUUID();
        criarUsuario(subTecnicoA, a, UserRole.TECNICO);
        criarUsuario(subTecnicoB, b, UserRole.TECNICO);
        criarUsuario(subAtletaA, a, UserRole.ATLETA);
    }

    private String paceBody(int segKm) {
        return """
                {"pace":{"limiarSegKm":%d,"dataTeste":"%s","origem":"TESTE_CAMPO"}}""".formatted(segKm, dataTeste);
    }

    private String fcBody(int limiar, Integer maxima) {
        String max = maxima == null ? "" : ",\"maxima\":" + maxima;
        return """
                {"fc":{"limiar":%d%s,"dataTeste":"%s","origem":"PROVA"}}""".formatted(limiar, max, dataTeste);
    }

    private org.springframework.test.web.servlet.ResultActions putAncoras(UUID id, String body) throws Exception {
        return mockMvc.perform(put(URL + "/ancoras", id).with(tecnicoA()).contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private org.springframework.test.web.servlet.ResultActions simular(UUID id, String body) throws Exception {
        return mockMvc.perform(post(URL + "/simulacao", id).with(tecnicoA()).contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private JsonNode getZonas(UUID id) throws Exception {
        String json = mockMvc.perform(get(URL, id).with(tecnicoA())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    private Atleta relido() {
        return atletaRepository.findById(atletaId).orElseThrow();
    }

    @Nested
    @DisplayName("gravação")
    class Gravacao {

        @Test
        @DisplayName("CA1: pace 290 em atleta sem pace grava 4.83, a origem e as seis zonas com pace")
        void cadastraPace() throws Exception {
            putAncoras(atletaId, paceBody(290))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.disponibilidade.pace").value(true))
                    .andExpect(jsonPath("$.ancoras.paceLimiarMinKm").value(4.83))
                    .andExpect(jsonPath("$.ancoras.fonteLimiarPace").value("TESTE_CAMPO"))
                    .andExpect(jsonPath("$.zonas[1].pace.minSegKm").value(304))
                    .andExpect(jsonPath("$.zonas[1].pace.maxSegKm").value(333));

            Atleta salvo = relido();
            assertThat(salvo.getPaceLimiar()).isEqualByComparingTo("4.83");
            assertThat(salvo.getFonteLimiarPace()).isEqualTo(FonteLimiar.TESTE_CAMPO);
            assertThat(salvo.getDataUltimoTestePace()).isEqualTo(LocalDate.parse(dataTeste));
        }

        @Test
        @DisplayName("CA2: limiar e máxima informados ficam como valor medido")
        void cadastraFcComMaxima() throws Exception {
            putAncoras(atletaId, fcBody(168, 186))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ancoras.fcLimiar").value(168))
                    .andExpect(jsonPath("$.ancoras.fcLimiarEstimada").value(false))
                    .andExpect(jsonPath("$.ancoras.fonteLimiarFc").value("PROVA"))
                    .andExpect(jsonPath("$.fcMaxima.bpm").value(186))
                    .andExpect(jsonPath("$.fcMaxima.estimada").value(false));
        }

        @Test
        @DisplayName("CA2: máxima vazia mantém a gravada; sem gravada, continua estimada")
        void maximaVazia() throws Exception {
            UUID comMaxima = criarAtleta(assessoriaRepository.findById(tenantAId).orElseThrow(), null, null, 190);
            putAncoras(comMaxima, fcBody(168, null))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.fcMaxima.bpm").value(190))
                    .andExpect(jsonPath("$.fcMaxima.estimada").value(false));

            putAncoras(atletaId, fcBody(168, null))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.fcMaxima.estimada").value(true));
            assertThat(relido().getFcMaxima()).isNull();
        }

        @Test
        @DisplayName("CA3: só pace não altera a FC, e só FC não altera o pace")
        void grupoAusenteMantemOEixo() throws Exception {
            putAncoras(atletaId, fcBody(168, 186)).andExpect(status().isOk());
            putAncoras(atletaId, paceBody(290)).andExpect(status().isOk());

            Atleta salvo = relido();
            assertThat(salvo.getFcLimiar()).isEqualTo(168);
            assertThat(salvo.getFcMaxima()).isEqualTo(186);
            assertThat(salvo.getFonteLimiarFc()).isEqualTo(FonteLimiar.PROVA);

            putAncoras(atletaId, fcBody(170, null)).andExpect(status().isOk());
            assertThat(relido().getPaceLimiar()).isEqualByComparingTo("4.83");
        }

        @Test
        @DisplayName("CA18: dois PUTs concorrentes, um só com fc e outro só com pace, gravam os dois eixos")
        void concorrenciaEmEixosDiferentes() throws Exception {
            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch largada = new CountDownLatch(1);
            try {
                Future<?> fc = pool.submit(() -> disparar(largada, fcBody(168, 186)));
                Future<?> pace = pool.submit(() -> disparar(largada, paceBody(290)));
                largada.countDown();
                fc.get(30, TimeUnit.SECONDS);
                pace.get(30, TimeUnit.SECONDS);
            } finally {
                pool.shutdownNow();
            }

            Atleta salvo = relido();
            assertThat(salvo.getFcLimiar()).isEqualTo(168);
            assertThat(salvo.getFcMaxima()).isEqualTo(186);
            assertThat(salvo.getPaceLimiar()).isEqualByComparingTo("4.83");
        }

        private void disparar(CountDownLatch largada, String body) {
            try {
                largada.await();
                putAncoras(atletaId, body).andExpect(status().isOk());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @Nested
    @DisplayName("limites do banco (CHECKs de V6 e V107)")
    class LimitesDoBanco {

        @ParameterizedTest(name = "pace {0} s/km aceito")
        @ValueSource(ints = {150, 151, 719, 720})
        @DisplayName("CA4: bordas válidas do pace passam pelo CHECK ck_atleta_pace_limiar")
        void paceNasBordas(int segKm) throws Exception {
            putAncoras(atletaId, paceBody(segKm)).andExpect(status().isOk());
            assertThat(relido().getPaceLimiar()).isNotNull();
        }

        @ParameterizedTest(name = "pace {0} s/km rejeitado")
        @ValueSource(ints = {0, 149, 721, 5000})
        @DisplayName("CA4: pace fora de 150..720 -> 400 com o campo e nada gravado")
        void paceForaDasBordas(int segKm) throws Exception {
            putAncoras(atletaId, paceBody(segKm))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields['pace.limiarSegKm']").exists());
            assertThat(relido().getPaceLimiar()).isNull();
        }

        @ParameterizedTest(name = "limiar {0} / máxima {1}")
        @CsvSource({"100,120", "100,220", "200,212", "200,220"})
        @DisplayName("CA4: bordas válidas de limiar e máxima passam pelos CHECKs de FC")
        void fcNasBordas(int limiar, int maxima) throws Exception {
            putAncoras(atletaId, fcBody(limiar, maxima)).andExpect(status().isOk());
            assertThat(relido().getFcLimiar()).isEqualTo(limiar);
        }

        @ParameterizedTest(name = "limiar {0} / máxima {1} -> campo {2}")
        @CsvSource({"99,150,fc.limiar", "201,220,fc.limiar", "100,119,fc.maxima", "100,221,fc.maxima",
                "168,169,fc.maxima", "168,177,fc.maxima", "200,211,fc.maxima"})
        @DisplayName("CA4: fora das bordas ou máxima abaixo do topo da Z5 -> 400 com o campo")
        void fcForaDasBordas(int limiar, int maxima, String campo) throws Exception {
            putAncoras(atletaId, fcBody(limiar, maxima))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields['" + campo + "']").exists());
            assertThat(relido().getFcLimiar()).isNull();
        }

        @Test
        @DisplayName("CA4: máxima 178 com limiar 168 é aceita (round(168 x 1,06))")
        void maximaNoTopoDaZ5() throws Exception {
            putAncoras(atletaId, fcBody(168, 178)).andExpect(status().isOk());
        }

        @Test
        @DisplayName("CA4: data futura, anterior a 2000, grupo ausente e origem desconhecida -> 400")
        void outrasRegras() throws Exception {
            String futura = """
                    {"pace":{"limiarSegKm":290,"dataTeste":"%s"}}""".formatted(LocalDate.now().plusDays(2));
            putAncoras(atletaId, futura).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields['pace.dataTeste']").exists());

            String antiga = """
                    {"pace":{"limiarSegKm":290,"dataTeste":"1999-12-31"}}""";
            putAncoras(atletaId, antiga).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields['pace.dataTeste']").exists());

            putAncoras(atletaId, "{}").andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields.body").exists());

            String origem = """
                    {"pace":{"limiarSegKm":290,"dataTeste":"%s","origem":"ADIVINHACAO"}}""".formatted(dataTeste);
            putAncoras(atletaId, origem).andExpect(status().isBadRequest());
            assertThat(relido().getPaceLimiar()).isNull();
        }
    }

    @Nested
    @DisplayName("simulação")
    class Simulacao {

        @Test
        @DisplayName("CA5: devolve as mesmas zonas do PUT e não altera tb_atleta (relida em transação separada)")
        void naoGrava() throws Exception {
            JsonNode antes = getZonas(atletaId);

            String simulado = simular(atletaId, paceBody(290)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.ancoras.paceLimiarMinKm").value(4.83))
                    .andReturn().getResponse().getContentAsString();

            assertThat(relido().getPaceLimiar()).isNull();
            assertThat(relido().getFonteLimiarPace()).isNull();
            assertThat(getZonas(atletaId)).isEqualTo(antes);

            String gravado = putAncoras(atletaId, paceBody(290)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(objectMapper.readTree(simulado)).isEqualTo(objectMapper.readTree(gravado));
        }

        @Test
        @DisplayName("aplica a mesma validação do PUT: máxima 169 com limiar 168 -> 400")
        void mesmaValidacao() throws Exception {
            simular(atletaId, fcBody(168, 169)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields['fc.maxima']").exists());
        }

        @ParameterizedTest(name = "{0} s/km")
        @ValueSource(ints = {150, 290, 333, 599, 720})
        @DisplayName("CA17: simulação, PUT e GET seguinte devolvem faixas idênticas")
        void simulacaoPutGetIdenticos(int segKm) throws Exception {
            String simulado = simular(atletaId, paceBody(segKm)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            String gravado = putAncoras(atletaId, paceBody(segKm)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            JsonNode lido = getZonas(atletaId);

            assertThat(objectMapper.readTree(simulado).get("zonas")).isEqualTo(objectMapper.readTree(gravado).get("zonas"));
            assertThat(lido.get("zonas")).isEqualTo(objectMapper.readTree(gravado).get("zonas"));
        }
    }

    @Nested
    @DisplayName("tenant e papel (CA6)")
    class TenantEPapel {

        @Test
        @DisplayName("PUT e simulação de TECNICO de outro tenant -> 404, sem gravar")
        void outroTenant() throws Exception {
            mockMvc.perform(put(URL + "/ancoras", atletaId).with(tecnicoB())
                            .contentType(MediaType.APPLICATION_JSON).content(paceBody(290)))
                    .andExpect(status().isNotFound());
            mockMvc.perform(post(URL + "/simulacao", atletaId).with(tecnicoB())
                            .contentType(MediaType.APPLICATION_JSON).content(paceBody(290)))
                    .andExpect(status().isNotFound());
            assertThat(relido().getPaceLimiar()).isNull();
        }

        @Test
        @DisplayName("ATLETA -> 403 no PUT e na simulação")
        void atletaBarrado() throws Exception {
            RequestPostProcessor atleta = jwtDe(subAtletaA, tenantAId, "ATLETA");
            mockMvc.perform(put(URL + "/ancoras", atletaId).with(atleta)
                            .contentType(MediaType.APPLICATION_JSON).content(paceBody(290)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post(URL + "/simulacao", atletaId).with(atleta)
                            .contentType(MediaType.APPLICATION_JSON).content(paceBody(290)))
                    .andExpect(status().isForbidden());
            assertThat(relido().getPaceLimiar()).isNull();
        }
    }

    private RequestPostProcessor tecnicoA() {
        return jwtDe(subTecnicoA, tenantAId, "TECNICO");
    }

    private RequestPostProcessor tecnicoB() {
        return jwtDe(subTecnicoB, tenantBId, "TECNICO");
    }

    private UUID criarAtleta(Assessoria assessoria, BigDecimal pace, Integer fcLimiar, Integer fcMaxima) {
        Atleta atleta = new Atleta();
        atleta.setNome("Atleta Âncoras");
        atleta.setEmail("ancoras-" + UUID.randomUUID() + "@menthoros.test");
        atleta.setObjetivo("Correr 10 km");
        atleta.setNivelExperiencia(NivelExperiencia.INTERMEDIARIO);
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setAssessoria(assessoria);
        atleta.setPaceLimiar(pace);
        atleta.setFcLimiar(fcLimiar);
        atleta.setFcMaxima(fcMaxima);
        return atletaRepository.save(atleta).getId();
    }

    private Assessoria criarAssessoria(String sufixo) {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria " + sufixo);
        assessoria.setDominio("ancoras-" + sufixo.toLowerCase() + "-" + UUID.randomUUID());
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
                .jwt(j -> j.subject(subject.toString()).claim("tenant_id", tenantId.toString())
                        .claim("email", subject + "@menthoros.test"));
    }
}
