package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.domain.planner.SeasonWeekSnapshot;
import br.com.menthoros.backend.dto.llm.PlanoSemanalLlmDto;
import br.com.menthoros.backend.dto.llm.TreinoPlanejadoLlmDto;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.entity.PlanoTreino;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.entity.SeasonWeek;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.ModoGeracaoPlano;
import br.com.menthoros.backend.enums.SeasonPlanStatus;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.SeasonPlanFixtures;
import br.com.menthoros.backend.services.IaService;
import br.com.menthoros.backend.services.PlanoService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plano semanal gerado com temporada ACTIVE contra Postgres real (add-season-plan-weekly-context, 4.1):
 * o loader acha a semana, o LLM (mockado) a recebe, o persister grava o vínculo e o desvio, e apagar
 * a semana da temporada não apaga o plano (FK da V119 com ON DELETE SET NULL).
 */
@TestPropertySource(properties = {"season-plan.enabled=true", "onboarding.migrate-existing.enabled=false"})
@DisplayName("plano semanal com semana da temporada")
class PlanoSemanaTemporadaIT extends AbstractIntegrationTest {

    private static final double TARGET_TSS = 50.0;

    @Autowired private PlanoService planoService;
    @Autowired private EntityManager em;
    @Autowired private TransactionTemplate tx;
    @Autowired private JdbcTemplate jdbc;

    @MockitoBean private IaService iaService;

    private Atleta atleta;
    private SeasonWeek semanaDoPlano;

    @BeforeEach
    void preparar() {
        // PROXIMA_SEMANA sem plano anterior: o loader resolve a próxima segunda-feira.
        LocalDate proximaSegunda = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        tx.executeWithoutResult(s -> {
            SeasonPlanFixtures fx = new SeasonPlanFixtures(em);
            Assessoria assessoria = fx.assessoria();
            atleta = fx.atleta(assessoria);
            atleta.setDiasDisponiveis(List.of(DiaSemana.SEGUNDA, DiaSemana.QUARTA, DiaSemana.SABADO));
            atleta.setDiaPreferidoLongo(DiaSemana.SABADO);
            Prova prova = fx.prova(atleta);
            PlanoTreino temporada = fx.planoTreino(atleta, prova, SeasonPlanStatus.ACTIVE);
            temporada.setDataInicio(proximaSegunda.minusWeeks(1));
            fx.semana(temporada, 1);
            semanaDoPlano = fx.semana(temporada, 2);
            semanaDoPlano.setTargetTss(TARGET_TSS);
            fx.semana(temporada, 3);
        });
        TenantContext.setTenantId(atleta.getAssessoria().getId());
    }

    @AfterEach
    void limpar() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("grava seasonWeekId e o desvio do TSS persistido; apagar a semana deixa o plano com vínculo nulo")
    void gravaVinculoEDesvio() {
        when(iaService.geraPlanoSemanalAvancado(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(planoDeUmTreino());

        PlanoSemanal plano = planoService.gerarPlanoTreino(atleta.getId(), ModoGeracaoPlano.PROXIMA_SEMANA);

        ArgumentCaptor<SeasonWeekSnapshot> semanaEnviada = ArgumentCaptor.forClass(SeasonWeekSnapshot.class);
        verify(iaService).geraPlanoSemanalAvancado(any(), any(), any(), any(), any(), any(), any(), any(),
                semanaEnviada.capture());
        SeasonWeekSnapshot enviada = semanaEnviada.getValue();
        assertThat(enviada.seasonWeekId()).isEqualTo(semanaDoPlano.getId());
        assertThat(enviada.weekIndex()).isEqualTo(2);
        assertThat(enviada.totalWeeks()).isEqualTo(3);

        Map<String, Object> linha = jdbc.queryForMap(
                "SELECT season_week_id, target_deviation_pct FROM tb_plano_semanal WHERE id = ?", plano.getId());
        Integer tssPersistido = jdbc.queryForObject(
                "SELECT COALESCE(SUM(tss_planejado), 0) FROM tb_treino_planejado WHERE plano_semanal_id = ?",
                Integer.class, plano.getId());
        BigDecimal esperado = BigDecimal.valueOf((tssPersistido - TARGET_TSS) / TARGET_TSS * 100)
                .setScale(1, RoundingMode.HALF_UP);
        assertThat(linha.get("season_week_id")).isEqualTo(semanaDoPlano.getId());
        assertThat((BigDecimal) linha.get("target_deviation_pct")).isEqualByComparingTo(esperado);

        jdbc.update("DELETE FROM tb_season_week WHERE id = ?", semanaDoPlano.getId());

        assertThat(jdbc.queryForObject("SELECT season_week_id FROM tb_plano_semanal WHERE id = ?", UUID.class,
                plano.getId())).as("ON DELETE SET NULL: o plano fica, o vínculo some").isNull();
    }

    private static PlanoSemanalLlmDto planoDeUmTreino() {
        TreinoPlanejadoLlmDto treino = new TreinoPlanejadoLlmDto(
                "SEGUNDA", "FACIL", "130-140 bpm", 40, 1.0, 4,
                "Base aeróbica", "45", 8.0, "5:40", List.of());
        return new PlanoSemanalLlmDto(8.0, 8.0, null, null, "PLANEJADO", "Semana de base", List.of(treino), List.of());
    }
}
