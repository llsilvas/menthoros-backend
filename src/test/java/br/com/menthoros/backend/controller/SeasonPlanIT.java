package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.MetricasDiarias;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.enums.UserRole;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.SeasonPlanFixtures;
import br.com.menthoros.backend.security.AuthenticatedAtletaResolver;
import br.com.menthoros.backend.security.AuthenticatedPrincipalResolver;
import br.com.menthoros.backend.services.ProvaService;
import br.com.menthoros.backend.services.SeasonPlanService;
import br.com.menthoros.backend.services.SeasonPlanService.Generation;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ponta a ponta do plano de temporada contra Postgres real (V116–V118), com a flag ligada:
 * gerar → ler, papel, isolamento entre assessorias e as duas corridas do D13.
 *
 * <p>Sem {@code @Transactional} na classe: cada chamada commita, para o lock pessimista e o índice
 * único parcial serem exercitados de verdade. Flag desligada e 403 com a flag desligada ficam no
 * {@code SeasonPlanControllerTest} — o {@code @PreAuthorize} roda antes do serviço em qualquer caso.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "season-plan.enabled=true")
@DisplayName("plano de temporada — gerar e ler")
class SeasonPlanIT extends AbstractIntegrationTest {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    @Autowired private MockMvc mockMvc;
    @Autowired private SeasonPlanService seasonPlanService;
    @Autowired private ProvaService provaService;
    @Autowired private EntityManager em;
    @Autowired private TransactionTemplate tx;
    @Autowired private JdbcTemplate jdbc;

    @MockitoBean private AuthenticatedAtletaResolver atletaResolver;
    @MockitoBean private AuthenticatedPrincipalResolver principalResolver;

    private Atleta atleta;
    private Prova prova;
    private UUID tenantId;
    private UUID subTecnico;
    private UUID subAtleta;
    private UUID subTecnicoOutraAssessoria;

    @BeforeEach
    void preparar() {
        when(atletaResolver.atuaComoAtleta()).thenReturn(false);
        when(principalResolver.hasRole(UserRole.ADMIN)).thenReturn(false);
        LocalDate hoje = LocalDate.now(FUSO);
        subTecnico = UUID.randomUUID();
        subAtleta = UUID.randomUUID();
        subTecnicoOutraAssessoria = UUID.randomUUID();
        tx.executeWithoutResult(s -> {
            SeasonPlanFixtures fx = new SeasonPlanFixtures(em);
            Assessoria assessoria = fx.assessoria();
            atleta = fx.atleta(assessoria);
            prova = fx.prova(atleta);
            prova.setDataProva(hoje.plusWeeks(19));
            tenantId = assessoria.getId();
            metrica(hoje.minusDays(60), 30.0);
            metrica(hoje.minusDays(1), 40.0);
            treino(hoje.minusDays(10), "35.0");
            usuario(subTecnico, assessoria, UserRole.TECNICO);
            usuario(subAtleta, assessoria, UserRole.ATLETA);
            usuario(subTecnicoOutraAssessoria, fx.assessoria(), UserRole.TECNICO);
        });
    }

    @AfterEach
    void limpar() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("treinador gera a temporada (201) e a lê de volta (200) com 20 semanas PROPOSED")
    void gerarELer() throws Exception {
        String base = "/api/v1/atletas/" + atleta.getId() + "/season-plans";

        mockMvc.perform(get(base + "/current").with(como(subTecnico, tenantId, "TECNICO")))
                .andExpect(status().isNoContent());
        mockMvc.perform(post(base).with(como(subTecnico, tenantId, "TECNICO")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.seasonStatus").value("PROPOSED"))
                .andExpect(jsonPath("$.weeks.length()").value(20))
                .andExpect(jsonPath("$.weeks[19].phase").value("RACE_WEEK"));
        mockMvc.perform(post(base).with(como(subTecnico, tenantId, "TECNICO")))
                .andExpect(status().isOk());
        mockMvc.perform(get(base + "/current").with(como(subTecnico, tenantId, "TECNICO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provaId").value(prova.getId().toString()))
                .andExpect(jsonPath("$.weeks.length()").value(20));

        assertThat(contarAbertas()).isEqualTo(1);
    }

    @Test
    @DisplayName("prova preparatória vira marco; POST e GET devolvem os mesmos marcos e validações")
    void marcosEValidacoesIdaEVolta() throws Exception {
        String base = "/api/v1/atletas/" + atleta.getId() + "/season-plans";
        UUID meiaId = tx.execute(s -> {
            Prova meia = new SeasonPlanFixtures(em).prova(em.find(Atleta.class, atleta.getId()));
            meia.setNomeProva("Meia preparatória");
            meia.setDistancia(br.com.menthoros.backend.enums.DistanciaProva.KM_21);
            meia.setDataProva(LocalDate.now(FUSO).plusWeeks(6));
            meia.setProvaAlvo(false);
            return meia.getId();
        });

        String criada = mockMvc.perform(post(base).with(como(subTecnico, tenantId, "TECNICO")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.milestones.length()").value(1))
                .andExpect(jsonPath("$.milestones[0].provaId").value(meiaId.toString()))
                .andExpect(jsonPath("$.validations").isArray())
                .andReturn().getResponse().getContentAsString();
        String lida = mockMvc.perform(get(base + "/current").with(como(subTecnico, tenantId, "TECNICO")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat((Object) com.jayway.jsonpath.JsonPath.read(lida, "$.milestones"))
                .isEqualTo(com.jayway.jsonpath.JsonPath.read(criada, "$.milestones"));
        assertThat((Object) com.jayway.jsonpath.JsonPath.read(lida, "$.validations"))
                .isEqualTo(com.jayway.jsonpath.JsonPath.read(criada, "$.validations"));
        assertThat(jdbc.queryForObject(
                "select count(*) from tb_season_week where milestone_race_id = ?", Integer.class, meiaId))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("ATLETA recebe 403 em gerar e ler")
    void atletaProibido() throws Exception {
        String base = "/api/v1/atletas/" + atleta.getId() + "/season-plans";

        mockMvc.perform(post(base).with(como(subAtleta, tenantId, "ATLETA"))).andExpect(status().isForbidden());
        mockMvc.perform(get(base + "/current").with(como(subAtleta, tenantId, "ATLETA"))).andExpect(status().isForbidden());
        assertThat(contarAbertas()).isZero();
    }

    @Test
    @DisplayName("treinador de outra assessoria não gera nem lê a temporada do atleta")
    void outraAssessoria() throws Exception {
        String base = "/api/v1/atletas/" + atleta.getId() + "/season-plans";
        UUID outroTenant = jdbc.queryForObject(
                "select tenant_id from tb_usuario where id = ?", UUID.class, subTecnicoOutraAssessoria);

        mockMvc.perform(post(base).with(como(subTecnicoOutraAssessoria, outroTenant, "TECNICO")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(base + "/current").with(como(subTecnicoOutraAssessoria, outroTenant, "TECNICO")))
                .andExpect(status().isForbidden());
        assertThat(contarAbertas()).isZero();
    }

    @Test
    @DisplayName("dois POST concorrentes geram uma só proposta")
    void postsConcorrentes() throws Exception {
        List<Generation> resultados = concorrentes(
                () -> seasonPlanService.generate(atleta.getId()),
                () -> seasonPlanService.generate(atleta.getId()));

        assertThat(resultados).extracting(Generation::created).containsExactlyInAnyOrder(true, false);
        assertThat(contarAbertas()).isEqualTo(1);
    }

    @Test
    @DisplayName("POST e cancelamento da prova concorrentes não deixam temporada aberta")
    void postECancelamentoConcorrentes() throws Exception {
        concorrentes(
                () -> {
                    try {
                        return seasonPlanService.generate(atleta.getId());
                    } catch (DomainRuleViolationException e) {
                        return null; // o cancelamento chegou antes: 422 é o resultado certo
                    }
                },
                () -> {
                    provaService.cancelarProva(atleta.getId(), prova.getId());
                    return null;
                });

        assertThat(contarAbertas()).isZero();
    }

    @SafeVarargs
    private <T> List<T> concorrentes(Callable<T>... tarefas) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tarefas.length);
        CountDownLatch largada = new CountDownLatch(1);
        try {
            List<Future<T>> futuros = new ArrayList<>();
            for (Callable<T> tarefa : tarefas) {
                futuros.add(pool.submit(() -> {
                    TenantContext.setTenantId(tenantId);
                    try {
                        largada.await();
                        return tarefa.call();
                    } finally {
                        TenantContext.clear();
                    }
                }));
            }
            largada.countDown();
            List<T> resultados = new ArrayList<>();
            for (Future<T> f : futuros) {
                resultados.add(f.get(30, TimeUnit.SECONDS));
            }
            return resultados;
        } finally {
            pool.shutdownNow();
        }
    }

    private int contarAbertas() {
        return jdbc.queryForObject(
                "select count(*) from tb_plano_treino where atleta_id = ? and season_status in ('PROPOSED','ACTIVE')",
                Integer.class, atleta.getId());
    }

    private RequestPostProcessor como(UUID subject, UUID tenant, String papel) {
        return jwt()
                .authorities(new SimpleGrantedAuthority("ROLE_" + papel))
                .jwt(j -> j.subject(subject.toString()).claim("tenant_id", tenant.toString()));
    }

    private void metrica(LocalDate data, double ctl) {
        MetricasDiarias m = new MetricasDiarias();
        m.setAtleta(atleta);
        m.setData(data);
        m.setCtl(ctl);
        m.setTenantId(atleta.getAssessoria().getId());
        em.persist(m);
    }

    private void treino(LocalDate data, String km) {
        TreinoRealizado t = new TreinoRealizado();
        t.setAtleta(atleta);
        t.setDataTreino(data);
        t.setDiaSemana(DiaSemana.SEGUNDA);
        t.setTipoTreino(TipoTreino.FACIL);
        t.setDuracaoMin(Duration.ofMinutes(60));
        t.setDistanciaKm(new BigDecimal(km));
        t.setTenantId(atleta.getAssessoria().getId());
        em.persist(t);
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
