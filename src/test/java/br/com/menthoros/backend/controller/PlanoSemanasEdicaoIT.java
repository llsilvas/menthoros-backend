package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.enums.PlanoReviewStatus;
import br.com.menthoros.backend.enums.PlanoStatus;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.enums.TreinoExecucaoStatus;
import br.com.menthoros.backend.enums.UserRole;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.IntegracaoExternaRepository;
import br.com.menthoros.backend.repository.PlanoMetadadosRepository;
import br.com.menthoros.backend.repository.PlanoSemanalRepository;
import br.com.menthoros.backend.repository.TreinoPlanejadoRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato de {@code GET /api/v1/planos/atletas/{id}/semanas} com os campos de edição em plano aprovado
 * (editar-treino-aprovado-planos-dialog): serialização real, isolamento, o caminho do atleta sem os
 * campos novos e o orçamento de queries do enriquecimento.
 */
@AutoConfigureMockMvc
@DisplayName("Listagem de semanas do coach — edição de treino aprovado")
class PlanoSemanasEdicaoIT extends AbstractIntegrationTest {

    // "Hoje" do atleta sem timezone (fuso padrão do AtletaHojeResolver)
    private static final LocalDate HOJE = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
    private static final Instant EDITADO_EM = Instant.parse("2026-11-04T10:00:00Z");

    @Autowired private MockMvc mockMvc;
    @Autowired private AssessoriaRepository assessoriaRepository;
    @Autowired private AtletaRepository atletaRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PlanoMetadadosRepository planoMetadadosRepository;
    @Autowired private PlanoSemanalRepository planoSemanalRepository;
    @Autowired private TreinoPlanejadoRepository treinoPlanejadoRepository;
    @Autowired private IntegracaoExternaRepository integracaoRepository;
    @Autowired private EntityManagerFactory entityManagerFactory;

    private Assessoria assessoria;
    private Atleta atleta;
    private UUID subTecnico;
    private UUID subAtleta;
    private PlanoMetaDados meta;

    @BeforeEach
    void setUp() {
        assessoria = criarAssessoria();
        subTecnico = UUID.randomUUID();
        subAtleta = UUID.randomUUID();
        criarUsuario(subTecnico, assessoria, UserRole.TECNICO);
        atleta = criarAtleta(assessoria, criarUsuario(subAtleta, assessoria, UserRole.ATLETA));
        meta = new PlanoMetaDados();
        meta.setAtleta(atleta);
        meta.setAssessoria(assessoria);
        meta.setDiaPreferidoLongo(DiaSemana.SABADO);
        meta = planoMetadadosRepository.save(meta);
    }

    @AfterEach
    void tearDown() {
        statistics().setStatisticsEnabled(false);
    }

    @Nested
    @DisplayName("GET semanas (TECNICO)")
    class Semanas {

        @Test
        @DisplayName("plano aprovado em andamento: editable/editRejection por treino, editedAfterApprovalAt ISO e canal Garmin ativo")
        void aprovadoEmAndamento() throws Exception {
            PlanoSemanal plano = criarPlano(0, PlanoStatus.EM_ANDAMENTO, PlanoReviewStatus.APROVADO);
            TreinoPlanejado amanha = criarTreino(plano, HOJE.plusDays(1), TreinoExecucaoStatus.PENDENTE, EDITADO_EM);
            TreinoPlanejado hoje = criarTreino(plano, HOJE, TreinoExecucaoStatus.PENDENTE, null);
            criarConexao(FonteDados.GARMIN, true);

            mockMvc.perform(get("/api/v1/planos/atletas/{id}/semanas", atleta.getId()).with(jwtDe(subTecnico, "TECNICO")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].conexaoCanal.plataforma.value").value("GARMIN"))
                    .andExpect(jsonPath("$[0].conexaoCanal.ativo").value(true))
                    .andExpect(jsonPath("$[0].treinosPlanejados[?(@.id=='%s')].editable", amanha.getId()).value(true))
                    .andExpect(jsonPath("$[0].treinosPlanejados[?(@.id=='%s')].editRejection", amanha.getId()).isEmpty())
                    .andExpect(jsonPath("$[0].treinosPlanejados[?(@.id=='%s')].editedAfterApprovalAt", amanha.getId())
                            .value("2026-11-04T10:00:00Z"))
                    .andExpect(jsonPath("$[0].treinosPlanejados[?(@.id=='%s')].editable", hoje.getId()).value(false))
                    .andExpect(jsonPath("$[0].treinosPlanejados[?(@.id=='%s')].editRejection", hoje.getId())
                            .value("TODAY_ON_WATCH"))
                    .andExpect(jsonPath("$[0].treinosPlanejados[?(@.id=='%s')].editedAfterApprovalAt", hoje.getId()).isEmpty());
        }

        @Test
        @DisplayName("conexão revogada: conexaoCanal.ativo=false e treino de hoje TODAY_ON_WATCH")
        void conexaoRevogada() throws Exception {
            PlanoSemanal plano = criarPlano(0, PlanoStatus.EM_ANDAMENTO, PlanoReviewStatus.APROVADO);
            criarTreino(plano, HOJE, TreinoExecucaoStatus.PENDENTE, null);
            criarConexao(FonteDados.GARMIN, false);

            mockMvc.perform(get("/api/v1/planos/atletas/{id}/semanas", atleta.getId()).with(jwtDe(subTecnico, "TECNICO")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].conexaoCanal.ativo").value(false))
                    .andExpect(jsonPath("$[0].treinosPlanejados[0].editRejection").value("TODAY_ON_WATCH"));
        }

        @Test
        @DisplayName("sem conexão: conexaoCanal ausente e treino de hoje editável")
        void semConexao() throws Exception {
            PlanoSemanal plano = criarPlano(0, PlanoStatus.EM_ANDAMENTO, PlanoReviewStatus.APROVADO);
            criarTreino(plano, HOJE, TreinoExecucaoStatus.PENDENTE, null);

            mockMvc.perform(get("/api/v1/planos/atletas/{id}/semanas", atleta.getId()).with(jwtDe(subTecnico, "TECNICO")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].conexaoCanal").doesNotExist())
                    .andExpect(jsonPath("$[0].treinosPlanejados[0].editable").value(true));
        }

        @Test
        @DisplayName("plano concluído mantém só o selo; plano em revisão não tem nenhum campo de edição")
        void concluidoERevisao() throws Exception {
            PlanoSemanal revisao = criarPlano(1, PlanoStatus.PLANEJADO, PlanoReviewStatus.AGUARDANDO_REVISAO);
            criarTreino(revisao, HOJE.plusDays(8), TreinoExecucaoStatus.PENDENTE, EDITADO_EM);
            PlanoSemanal concluido = criarPlano(-1, PlanoStatus.CONCLUIDO, PlanoReviewStatus.APROVADO);
            criarTreino(concluido, HOJE.minusDays(6), TreinoExecucaoStatus.REALIZADO, EDITADO_EM);

            // em andamento primeiro, depois concluídas
            mockMvc.perform(get("/api/v1/planos/atletas/{id}/semanas", atleta.getId()).with(jwtDe(subTecnico, "TECNICO")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(revisao.getId().toString()))
                    .andExpect(jsonPath("$[0].treinosPlanejados[0].editable").doesNotExist())
                    .andExpect(jsonPath("$[0].treinosPlanejados[0].editRejection").doesNotExist())
                    .andExpect(jsonPath("$[0].treinosPlanejados[0].editedAfterApprovalAt").doesNotExist())
                    .andExpect(jsonPath("$[1].id").value(concluido.getId().toString()))
                    .andExpect(jsonPath("$[1].treinosPlanejados[0].editable").doesNotExist())
                    .andExpect(jsonPath("$[1].treinosPlanejados[0].editRejection").doesNotExist())
                    .andExpect(jsonPath("$[1].treinosPlanejados[0].editedAfterApprovalAt").value("2026-11-04T10:00:00Z"));
        }

        @Test
        @DisplayName("TECNICO de outro tenant não vê as semanas do atleta (lista vazia, como antes)")
        void outroTenant() throws Exception {
            PlanoSemanal plano = criarPlano(0, PlanoStatus.EM_ANDAMENTO, PlanoReviewStatus.APROVADO);
            criarTreino(plano, HOJE.plusDays(1), TreinoExecucaoStatus.PENDENTE, null);
            criarConexao(FonteDados.GARMIN, true);
            Assessoria outra = criarAssessoria();
            UUID subOutro = UUID.randomUUID();
            criarUsuario(subOutro, outra, UserRole.TECNICO);

            mockMvc.perform(get("/api/v1/planos/atletas/{id}/semanas", atleta.getId())
                            .with(jwtDe(subOutro, "TECNICO", outra.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isEmpty());
        }

        @Test
        @DisplayName("ROLE_ATLETA recebe 403 na listagem do coach")
        void atletaProibido() throws Exception {
            mockMvc.perform(get("/api/v1/planos/atletas/{id}/semanas", atleta.getId()).with(jwtDe(subAtleta, "ATLETA")))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("GET plano (ATLETA)")
    class PlanoDoAtleta {

        @Test
        @DisplayName("o JSON do atleta não contém nenhum dos campos novos")
        void semCamposNovos() throws Exception {
            PlanoSemanal plano = criarPlano(0, PlanoStatus.EM_ANDAMENTO, PlanoReviewStatus.APROVADO);
            criarTreino(plano, HOJE.plusDays(1), TreinoExecucaoStatus.PENDENTE, EDITADO_EM);
            criarTreino(plano, HOJE.minusDays(1), TreinoExecucaoStatus.PENDENTE, null);
            criarConexao(FonteDados.GARMIN, true);

            String body = mockMvc.perform(get("/api/v1/planos/{id}", atleta.getId()).with(jwtDe(subAtleta, "ATLETA")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.treinosPlanejados.length()").value(2))
                    .andReturn().getResponse().getContentAsString();

            assertThat(body).doesNotContain("\"editable\"", "\"editRejection\"", "\"editedAfterApprovalAt\"",
                    "\"conexaoCanal\"");
        }
    }

    @Nested
    @DisplayName("orçamento de queries")
    class Queries {

        /**
         * Medido em 2026-11-05 contra a base {@code e451e58} (mesmo cenário): 9 → 10 statements com 1 semana
         * e 23 → 24 com 3 semanas. O enriquecimento só lê entidades já carregadas pelo mapper; o único
         * statement novo é a consulta de integração, que este teste fixa em uma por requisição.
         */
        @Test
        @DisplayName("uma consulta de integração por requisição, com 1 ou 3 semanas de tamanhos diferentes")
        void umaConsultaDeIntegracao() throws Exception {
            criarConexao(FonteDados.GARMIN, true);
            PlanoSemanal s1 = criarPlano(0, PlanoStatus.EM_ANDAMENTO, PlanoReviewStatus.APROVADO);
            criarTreino(s1, HOJE.plusDays(1), TreinoExecucaoStatus.PENDENTE, null);

            long umaSemana = consultasDeIntegracaoNaListagem();

            PlanoSemanal s2 = criarPlano(1, PlanoStatus.PLANEJADO, PlanoReviewStatus.APROVADO);
            criarTreino(s2, HOJE.plusDays(8), TreinoExecucaoStatus.PENDENTE, null);
            criarTreino(s2, HOJE.plusDays(9), TreinoExecucaoStatus.PENDENTE, null);
            PlanoSemanal s3 = criarPlano(2, PlanoStatus.PLANEJADO, PlanoReviewStatus.APROVADO);
            criarTreino(s3, HOJE.plusDays(15), TreinoExecucaoStatus.PENDENTE, null);
            criarTreino(s3, HOJE.plusDays(16), TreinoExecucaoStatus.PENDENTE, null);
            criarTreino(s3, HOJE.plusDays(17), TreinoExecucaoStatus.PENDENTE, null);

            long tresSemanas = consultasDeIntegracaoNaListagem();

            assertThat(umaSemana).isEqualTo(1);
            assertThat(tresSemanas).isEqualTo(1);
        }

        private long consultasDeIntegracaoNaListagem() throws Exception {
            Statistics stats = statistics();
            stats.setStatisticsEnabled(true);
            stats.clear();
            mockMvc.perform(get("/api/v1/planos/atletas/{id}/semanas", atleta.getId()).with(jwtDe(subTecnico, "TECNICO")))
                    .andExpect(status().isOk());
            long integracao = 0;
            for (String query : stats.getQueries()) {
                if (query.contains("tb_integracao_externa")) {
                    integracao += stats.getQueryStatistics(query).getExecutionCount();
                }
            }
            return integracao;
        }
    }

    // ===== Helpers =====

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    private Assessoria criarAssessoria() {
        Assessoria a = new Assessoria();
        a.setNome("Assessoria Semanas IT");
        a.setDominio("semanas-it-" + UUID.randomUUID());
        a.setPlano(PlanoAssessoria.BASIC);
        return assessoriaRepository.save(a);
    }

    private Usuario criarUsuario(UUID subject, Assessoria a, UserRole role) {
        return usuarioRepository.save(Usuario.builder()
                .id(subject)
                .keycloakId(subject.toString())
                .assessoria(a)
                .email(subject + "@menthoros.test")
                .nome("Usuario " + subject)
                .role(role)
                .ativo(true)
                .build());
    }

    private Atleta criarAtleta(Assessoria a, Usuario usuario) {
        Atleta at = new Atleta();
        at.setNome("Atleta Semanas");
        at.setEmail(usuario.getEmail());
        at.setObjetivo("Correr 10km");
        at.setNivelExperiencia(NivelExperiencia.INTERMEDIARIO);
        at.setAtivo(AtletaStatus.ATIVO);
        at.setAssessoria(a);
        at.setUsuario(usuario);
        return atletaRepository.save(at);
    }

    /** {@code semanas} desloca a semana a partir de hoje: 0 = esta, -1 = anterior. */
    private PlanoSemanal criarPlano(int semanas, PlanoStatus status, PlanoReviewStatus review) {
        PlanoSemanal plano = new PlanoSemanal();
        plano.setAtleta(atleta);
        plano.setAssessoria(assessoria);
        plano.setPlanoMetaDados(meta);
        plano.setSemanaInicio(HOJE.plusWeeks(semanas));
        plano.setSemanaFim(HOJE.plusWeeks(semanas).plusDays(6));
        plano.setVolumePlanejadoKm(BigDecimal.valueOf(40));
        plano.setStatus(status);
        plano.setReviewStatus(review);
        plano.setObjetivoSemanal("Semana de teste");
        return planoSemanalRepository.save(plano);
    }

    private TreinoPlanejado criarTreino(PlanoSemanal plano, LocalDate data, TreinoExecucaoStatus status, Instant editadoEm) {
        TreinoPlanejado treino = new TreinoPlanejado();
        treino.setPlanoSemanal(plano);
        treino.setAtleta(atleta);
        treino.setTenantId(assessoria.getId());
        treino.setDataTreino(data);
        treino.setDiaSemana(DiaSemana.SABADO);
        treino.setTipoTreino(TipoTreino.REGENERATIVO);
        treino.setDescricao("Rodagem leve");
        treino.setDuracaoMin(Duration.ofMinutes(65));
        treino.setStatusTreino(status);
        treino.setEditedAfterApprovalAt(editadoEm);
        return treinoPlanejadoRepository.save(treino);
    }

    private void criarConexao(FonteDados plataforma, boolean ativo) {
        IntegracaoExterna conexao = new IntegracaoExterna();
        conexao.setAtleta(atleta);
        conexao.setTenantId(assessoria.getId());
        conexao.setPlataforma(plataforma);
        conexao.setExternalAthleteId("ext-" + UUID.randomUUID());
        conexao.setAccessToken("token-it");
        conexao.setRefreshToken("refresh-it");
        conexao.setTokenExpiraEm(Instant.now().plus(Duration.ofHours(1)));
        conexao.setScopes("WORKOUT_IMPORT,ACTIVITY_EXPORT");
        conexao.setAtivo(ativo);
        integracaoRepository.save(conexao);
    }

    private RequestPostProcessor jwtDe(UUID subject, String papel) {
        return jwtDe(subject, papel, assessoria.getId());
    }

    private RequestPostProcessor jwtDe(UUID subject, String papel, UUID tenant) {
        return jwt()
                .authorities(new SimpleGrantedAuthority("ROLE_" + papel))
                .jwt(j -> j.subject(subject.toString()).claim("tenant_id", tenant.toString()));
    }
}
