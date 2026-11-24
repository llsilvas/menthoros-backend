package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.entity.PlanoTreino;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.entity.SeasonWeek;
import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.enums.SeasonPlanStatus;
import br.com.menthoros.backend.enums.UserRole;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.SeasonPlanFixtures;
import br.com.menthoros.backend.security.AuthenticatedPrincipalResolver;
import br.com.menthoros.backend.services.SeasonPlanAdjustmentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ajuste da temporada contra Postgres real (add-season-plan-adjust-v2): rascunho, edição com travas,
 * aprovação com motivo e troca atômica de versões, vínculo dos planos semanais e concorrência.
 *
 * <p>Sem {@code @Transactional} na classe: cada chamada commita, para o lock da prova e os índices
 * únicos (V116/V118) serem exercitados de verdade.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "season-plan.enabled=true")
@DisplayName("plano de temporada — ajuste v2")
class SeasonPlanAdjustIT extends AbstractIntegrationTest {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    @Autowired private MockMvc mockMvc;
    @Autowired private SeasonPlanAdjustmentService adjustmentService;
    @Autowired private EntityManager em;
    @Autowired private TransactionTemplate tx;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private AuthenticatedPrincipalResolver principalResolver;

    private Atleta atleta;
    private PlanoTreino v1;
    private List<SeasonWeek> semanasV1;
    private PlanoSemanal planoSemanal;
    private UUID tenantId;
    private UUID subTecnico;
    private UUID subOutraAssessoria;
    private UUID outroTenant;
    private String base;

    @BeforeEach
    void preparar() {
        subTecnico = UUID.randomUUID();
        subOutraAssessoria = UUID.randomUUID();
        // Semana 1 concluída, semana 2 em andamento, 3 a 5 futuras.
        LocalDate inicio = LocalDate.now(FUSO).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1);
        tx.executeWithoutResult(s -> {
            SeasonPlanFixtures fx = new SeasonPlanFixtures(em);
            Assessoria assessoria = fx.assessoria();
            atleta = fx.atleta(assessoria);
            Prova prova = fx.prova(atleta);
            v1 = fx.planoTreino(atleta, prova, SeasonPlanStatus.ACTIVE);
            v1.setDataInicio(inicio);
            v1.setVersion(1);
            v1.setTotalWeeks(5);
            semanasV1 = List.of(fx.semana(v1, 1), fx.semana(v1, 2), fx.semana(v1, 3), fx.semana(v1, 4), fx.semana(v1, 5));
            semanasV1.forEach(w -> w.setTargetTss(300.0));
            planoSemanal = fx.planoSemanalAprovado(v1);
            planoSemanal.setSeasonWeekId(semanasV1.get(0).getId());
            tenantId = assessoria.getId();
            usuario(subTecnico, assessoria, UserRole.TECNICO);
            Assessoria outra = fx.assessoria();
            outroTenant = outra.getId();
            usuario(subOutraAssessoria, outra, UserRole.TECNICO);
        });
        when(principalResolver.getCurrentSubject()).thenReturn(subTecnico.toString());
        base = "/api/v1/atletas/" + atleta.getId() + "/season-plans";
    }

    @AfterEach
    void limpar() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("ajustar, editar a futura, aprovar com motivo: v1 arquiva, v2 ativa, passado idêntico, plano semanal fica na v1")
    void fluxoCompleto() throws Exception {
        String criado = mockMvc.perform(post(base + "/" + v1.getId() + "/draft").with(tecnico()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.seasonStatus").value("PROPOSED"))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.baseSeasonPlanId").value(v1.getId().toString()))
                .andReturn().getResponse().getContentAsString();
        UUID v2 = UUID.fromString(objectMapper.readTree(criado).get("id").asText());
        mockMvc.perform(post(base + "/" + v1.getId() + "/draft").with(tecnico()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(v2.toString()));
        mockMvc.perform(get(base + "/current").with(tecnico()))
                .andExpect(jsonPath("$.id").value(v1.getId().toString()))
                .andExpect(jsonPath("$.openDraftId").value(v2.toString()));

        mockMvc.perform(patch(base + "/" + v2 + "/weeks/4").with(tecnico()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetVolumeKm\":55,\"keySessions\":\"6x1km\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weeks[3].valueSource").value("COACH"))
                .andExpect(jsonPath("$.peakVolumeKm").value(55));
        mockMvc.perform(get(base + "/" + v2).with(tecnico()))
                .andExpect(jsonPath("$.weeks[3].keySessions").value("6x1km"))
                .andExpect(jsonPath("$.weeks[0].lockReason").value("COMPLETED"))
                .andExpect(jsonPath("$.weeks[1].lockReason").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.weeks[2].locked").value(false));
        mockMvc.perform(patch(base + "/" + v2 + "/weeks/2").with(tecnico()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetVolumeKm\":60}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("em andamento")));

        mockMvc.perform(post(base + "/" + v2 + "/approve").with(tecnico()))
                .andExpect(status().isUnprocessableEntity());
        assertThat(seasonStatus(v1.getId())).isEqualTo("ACTIVE");

        mockMvc.perform(post(base + "/" + v2 + "/approve").with(tecnico()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Atleta perdeu duas semanas\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seasonStatus").value("ACTIVE"));

        assertThat(seasonStatus(v1.getId())).isEqualTo("ARCHIVED");
        assertThat(seasonStatus(v2)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("select version_reason from tb_plano_treino where id = ?", String.class, v2))
                .isEqualTo("Atleta perdeu duas semanas");
        List<Map<String, Object>> antes = semanas(v1.getId());
        List<Map<String, Object>> depois = semanas(v2);
        assertThat(depois.subList(0, 2)).isEqualTo(antes.subList(0, 2));
        assertThat((BigDecimal) depois.get(3).get("target_volume_km")).isEqualByComparingTo("55");
        assertThat(jdbc.queryForMap("select plano_treino_id, season_week_id from tb_plano_semanal where id = ?",
                planoSemanal.getId()))
                .containsEntry("plano_treino_id", v1.getId())
                .containsEntry("season_week_id", semanasV1.get(0).getId());

        mockMvc.perform(delete(base + "/" + v2).with(tecnico())).andExpect(status().isConflict());
        mockMvc.perform(get(base + "/current").with(tecnico())).andExpect(jsonPath("$.id").value(v2.toString()));
    }

    @Test
    @DisplayName("dois 'Ajustar' simultâneos devolvem o mesmo rascunho e deixam uma linha só")
    void ajustesConcorrentes() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch largada = new CountDownLatch(1);
        try {
            Future<UUID> a = pool.submit(() -> criarDraftConcorrente(largada));
            Future<UUID> b = pool.submit(() -> criarDraftConcorrente(largada));
            largada.countDown();

            assertThat(a.get(30, TimeUnit.SECONDS)).isEqualTo(b.get(30, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject(
                "select count(*) from tb_plano_treino where atleta_id = ? and season_status = 'PROPOSED'",
                Integer.class, atleta.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("ativa arquivada depois de o ajuste começar: aprovar responde 409 e nada muda")
    void baseTrocada() throws Exception {
        TenantContext.setTenantId(tenantId);
        UUID v2 = adjustmentService.createDraft(atleta.getId(), v1.getId()).plan().id();
        TenantContext.clear();
        jdbc.update("update tb_plano_treino set season_status = 'ARCHIVED' where id = ?", v1.getId());

        mockMvc.perform(post(base + "/" + v2 + "/approve").with(tecnico()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Ajuste\"}"))
                .andExpect(status().isConflict());

        assertThat(seasonStatus(v2)).isEqualTo("PROPOSED");
    }

    @Test
    @DisplayName("descartar o ajuste deixa a v1 ativa e permite ajustar de novo")
    void descartar() throws Exception {
        TenantContext.setTenantId(tenantId);
        UUID v2 = adjustmentService.createDraft(atleta.getId(), v1.getId()).plan().id();
        TenantContext.clear();

        mockMvc.perform(delete(base + "/" + v2).with(tecnico())).andExpect(status().isNoContent());

        assertThat(seasonStatus(v2)).isEqualTo("ARCHIVED");
        assertThat(seasonStatus(v1.getId())).isEqualTo("ACTIVE");
        mockMvc.perform(post(base + "/" + v1.getId() + "/draft").with(tecnico())).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("treinador de outra assessoria não alcança o ajuste")
    void outraAssessoria() throws Exception {
        mockMvc.perform(post(base + "/" + v1.getId() + "/draft").with(como(subOutraAssessoria, outroTenant, "TECNICO")))
                .andExpect(status().isForbidden());

        assertThat(jdbc.queryForObject(
                "select count(*) from tb_plano_treino where atleta_id = ? and season_status = 'PROPOSED'",
                Integer.class, atleta.getId())).isZero();
    }

    @Test
    @DisplayName("recalcular preserva travadas e a semana do treinador, o diff mostra o que mudou e a aprovação grava")
    void recalcularDiffAprovar() throws Exception {
        cicloDeCincoSemanas();
        String criado = mockMvc.perform(post(base + "/" + v1.getId() + "/draft").with(tecnico()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.initialVolumeKm").value(38.5))
                .andReturn().getResponse().getContentAsString();
        UUID v2 = UUID.fromString(objectMapper.readTree(criado).get("id").asText());
        mockMvc.perform(patch(base + "/" + v2 + "/weeks/3").with(tecnico()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"longRunKm\":20}"))
                .andExpect(status().isOk());
        List<Map<String, Object>> travadasAntes = semanas(v2).subList(0, 2);

        mockMvc.perform(post(base + "/" + v2 + "/recalculate").with(tecnico()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phaseWeeks\":{\"BASE\":2,\"BUILD\":1,\"PEAK\":0,\"TAPER\":1},\"startVolumeKm\":40}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.initialVolumeKm").value(40.0))
                .andExpect(jsonPath("$.weeks[2].valueSource").value("COACH"))
                .andExpect(jsonPath("$.weeks[2].phase").value("BASE"))
                .andExpect(jsonPath("$.weeks[3].phase").value("TAPER"))
                .andExpect(jsonPath("$.weeks[3].valueSource").value("ENGINE"))
                .andExpect(jsonPath("$.weeks[4].phase").value("RACE_WEEK"));
        assertThat(semanas(v2).subList(0, 2)).isEqualTo(travadasAntes);

        JsonNode diff = objectMapper.readTree(mockMvc.perform(get(base + "/" + v2 + "/diff").with(tecnico()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(diff.findValues("weekIndex")).extracting(JsonNode::asInt).containsOnly(3, 4);
        JsonNode longao = entrada(diff, 3, "longRunKm");
        assertThat(longao.get("before").isNull()).isTrue();
        assertThat(longao.get("after").decimalValue()).isEqualByComparingTo("20");
        assertThat(longao.get("source").asText()).isEqualTo("COACH");
        JsonNode fase = entrada(diff, 4, "phase");
        assertThat(fase.get("before").asText()).isEqualTo("BASE");
        assertThat(fase.get("after").asText()).isEqualTo("TAPER");
        assertThat(fase.get("source").asText()).isEqualTo("ENGINE");

        mockMvc.perform(post(base + "/" + v2 + "/approve").with(tecnico()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Polimento mais cedo\"}"))
                .andExpect(status().isOk());
        assertThat(seasonStatus(v2)).isEqualTo("ACTIVE");
        assertThat(semanas(v2).get(3)).containsEntry("phase", "TAPER");
        assertThat(jdbc.queryForObject("select initial_volume_km from tb_plano_treino where id = ?", BigDecimal.class, v2))
                .isEqualByComparingTo("40.0");
    }

    @Test
    @DisplayName("recalcular e pedir o diff da ACTIVE respondem 409; de outra assessoria, 403")
    void recalcularForaDoAjuste() throws Exception {
        cicloDeCincoSemanas();
        String corpo = "{\"phaseWeeks\":{\"BASE\":2,\"BUILD\":1,\"PEAK\":0,\"TAPER\":1},\"startVolumeKm\":40}";

        mockMvc.perform(post(base + "/" + v1.getId() + "/recalculate").with(tecnico())
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isConflict());
        mockMvc.perform(get(base + "/" + v1.getId() + "/diff").with(tecnico())).andExpect(status().isConflict());

        TenantContext.setTenantId(tenantId);
        UUID v2 = adjustmentService.createDraft(atleta.getId(), v1.getId()).plan().id();
        TenantContext.clear();
        RequestPostProcessor outra = como(subOutraAssessoria, outroTenant, "TECNICO");
        mockMvc.perform(post(base + "/" + v2 + "/recalculate").with(outra)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(base + "/" + v2 + "/diff").with(outra)).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("select initial_volume_km from tb_plano_treino where id = ?", BigDecimal.class, v2))
                .isEqualByComparingTo("38.5");
    }

    @Test
    @DisplayName("OpenAPI publica recalculate e diff, com o diff como array")
    void openApi() throws Exception {
        JsonNode paths = objectMapper.readTree(mockMvc.perform(get("/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("paths");
        String prefixo = "/api/v1/atletas/{atletaId}/season-plans/{seasonPlanId}";

        assertThat(paths.has(prefixo + "/recalculate")).isTrue();
        assertThat(paths.path(prefixo + "/recalculate").path("post").path("responses").has("422")).isTrue();
        JsonNode conteudo = paths.path(prefixo + "/diff").path("get").path("responses").path("200").path("content");
        assertThat(conteudo.isEmpty()).isFalse();
        assertThat(conteudo.elements().next().path("schema").path("type").asText()).isEqualTo("array");
    }

    /**
     * Deixa a v1 recalculável: prova no domingo da semana 5 (o ciclo fecha em 5), semana 5 da prova, CTL e
     * volume de partida gravados.
     */
    private void cicloDeCincoSemanas() {
        jdbc.update("update tb_prova set data_prova = ? where id = ?",
                v1.getDataInicio().plusWeeks(4).plusDays(6), v1.getProva().getId());
        jdbc.update("update tb_plano_treino set initial_ctl = 40, initial_volume_km = 38.5 where id = ?", v1.getId());
        jdbc.update("update tb_season_week set phase = 'RACE_WEEK' where season_plan_id = ? and week_index = 5", v1.getId());
    }

    private static JsonNode entrada(JsonNode diff, int semana, String campo) {
        for (JsonNode e : diff) {
            if (e.get("weekIndex").asInt() == semana && campo.equals(e.get("field").asText())) {
                return e;
            }
        }
        throw new AssertionError("sem alteração de " + campo + " na semana " + semana + ": " + diff);
    }

    private UUID criarDraftConcorrente(CountDownLatch largada) throws Exception {
        TenantContext.setTenantId(tenantId);
        try {
            largada.await();
            return adjustmentService.createDraft(atleta.getId(), v1.getId()).plan().id();
        } finally {
            TenantContext.clear();
        }
    }

    private String seasonStatus(UUID id) {
        return jdbc.queryForObject("select season_status from tb_plano_treino where id = ?", String.class, id);
    }

    private List<Map<String, Object>> semanas(UUID temporada) {
        return jdbc.queryForList("""
                select week_index, week_start, phase, target_volume_km, target_tss, long_run_km, deload, value_source
                  from tb_season_week where season_plan_id = ? order by week_index
                """, temporada);
    }

    private RequestPostProcessor tecnico() {
        return como(subTecnico, tenantId, "TECNICO");
    }

    private RequestPostProcessor como(UUID subject, UUID tenant, String papel) {
        return jwt()
                .authorities(new SimpleGrantedAuthority("ROLE_" + papel))
                .jwt(j -> j.subject(subject.toString()).claim("tenant_id", tenant.toString()));
    }

    private void usuario(UUID subject, Assessoria assessoria, UserRole role) {
        em.persist(Usuario.builder()
                .id(subject)
                .keycloakId(subject.toString())
                .assessoria(assessoria)
                .email(subject + "@menthoros.test")
                .nome("Usuario " + subject)
                .role(role)
                .ativo(true)
                .build());
    }
}
