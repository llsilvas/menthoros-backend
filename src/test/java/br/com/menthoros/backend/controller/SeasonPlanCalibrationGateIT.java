package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.AthleteBaselineState;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.MetricasDiarias;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.enums.UserRole;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.SeasonPlanFixtures;
import br.com.menthoros.backend.security.AuthenticatedAtletaResolver;
import br.com.menthoros.backend.security.AuthenticatedPrincipalResolver;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Gate de calibração da temporada (add-season-plan-calibration-gate) contra Postgres real, com o
 * shadow do planner ligado — sem ele a graduação não é avaliada e o gate não se aplica.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = {"season-plan.enabled=true", "planner-engine.shadow=true"})
@DisplayName("plano de temporada — gate de calibração")
class SeasonPlanCalibrationGateIT extends AbstractIntegrationTest {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManager em;
    @Autowired private TransactionTemplate tx;
    @Autowired private JdbcTemplate jdbc;

    @MockitoBean private AuthenticatedAtletaResolver atletaResolver;
    @MockitoBean private AuthenticatedPrincipalResolver principalResolver;

    private Atleta atleta;
    private UUID tenantId;
    private UUID subTecnico;
    private String base;

    @BeforeEach
    void preparar() {
        when(atletaResolver.atuaComoAtleta()).thenReturn(false);
        when(principalResolver.hasRole(UserRole.ADMIN)).thenReturn(false);
        LocalDate hoje = LocalDate.now(FUSO);
        subTecnico = UUID.randomUUID();
        when(principalResolver.getCurrentSubject()).thenReturn(subTecnico.toString());
        tx.executeWithoutResult(s -> {
            SeasonPlanFixtures fx = new SeasonPlanFixtures(em);
            Assessoria assessoria = fx.assessoria();
            atleta = fx.atleta(assessoria);
            fx.prova(atleta).setDataProva(hoje.plusWeeks(19));
            tenantId = assessoria.getId();
            metrica(hoje.minusDays(60), 30.0);
            metrica(hoje.minusDays(1), 40.0);
            fx.historicoDeVolume(atleta, hoje);
            em.persist(Usuario.builder()
                    .id(subTecnico)
                    .keycloakId(subTecnico.toString())
                    .assessoria(assessoria)
                    .email(subTecnico + "@menthoros.test")
                    .nome("Tecnico")
                    .role(UserRole.TECNICO)
                    .ativo(true)
                    .build());
        });
        base = "/api/v1/atletas/" + atleta.getId() + "/season-plans";
    }

    @AfterEach
    void limpar() {
        TenantContext.clear();
    }

    @ParameterizedTest(name = "calibração iniciada há {0} dias")
    @ValueSource(ints = {2, 40})
    @DisplayName("atleta em calibração (semana 1 e preso na semana 5+) recebe 422 e nada é gravado")
    void emCalibracaoNaoGera(int diasDesdeInicio) throws Exception {
        baseline(Instant.now().minus(Duration.ofDays(diasDesdeInicio)));

        mockMvc.perform(post(base).with(tecnico()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("calibração")));

        assertThat(contar("select count(*) from tb_plano_treino where atleta_id = ?")).isZero();
        assertThat(contar("select count(*) from tb_season_week w join tb_plano_treino p on p.id = w.season_plan_id"
                + " where p.atleta_id = ?")).isZero();
    }

    @Test
    @DisplayName("proposta gerada antes da calibração: gerar e aprovar respondem 422 e ela continua PROPOSED")
    void propostaAnteriorBloqueada() throws Exception {
        String id = JsonPath.read(mockMvc.perform(post(base).with(tecnico()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        baseline(Instant.now());

        mockMvc.perform(post(base).with(tecnico())).andExpect(status().isUnprocessableEntity());
        mockMvc.perform(post(base + "/" + id + "/approve").with(tecnico())).andExpect(status().isUnprocessableEntity());

        assertThat(jdbc.queryForObject("select season_status from tb_plano_treino where id = ?::uuid", String.class, id))
                .isEqualTo("PROPOSED");
    }

    @Test
    @DisplayName("baseline sem calibracaoIniciadaEm (graduado ou tier A) gera normalmente")
    void foraDeCalibracaoGera() throws Exception {
        baseline(null);

        mockMvc.perform(post(base).with(tecnico())).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("atleta sem linha de baseline gera normalmente")
    void semBaselineGera() throws Exception {
        mockMvc.perform(post(base).with(tecnico())).andExpect(status().isCreated());
    }

    private void baseline(Instant calibracaoIniciadaEm) {
        tx.executeWithoutResult(s -> {
            AthleteBaselineState b = new AthleteBaselineState();
            b.setAtleta(em.getReference(Atleta.class, atleta.getId()));
            b.setTenantId(tenantId);
            b.setCalculatedAt(Instant.now());
            b.setCalibracaoIniciadaEm(calibracaoIniciadaEm);
            em.persist(b);
        });
    }

    private void metrica(LocalDate data, double ctl) {
        MetricasDiarias m = new MetricasDiarias();
        m.setAtleta(atleta);
        m.setData(data);
        m.setCtl(ctl);
        m.setTenantId(tenantId);
        em.persist(m);
    }

    private int contar(String sql) {
        return jdbc.queryForObject(sql, Integer.class, atleta.getId());
    }

    private RequestPostProcessor tecnico() {
        return jwt()
                .authorities(new SimpleGrantedAuthority("ROLE_TECNICO"))
                .jwt(j -> j.subject(subTecnico.toString()).claim("tenant_id", tenantId.toString()));
    }
}
