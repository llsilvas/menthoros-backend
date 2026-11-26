package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.domain.planner.SeasonCutReason;
import br.com.menthoros.backend.domain.planner.WeekPlanSkeleton;
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
import br.com.menthoros.backend.services.PlanoReviewService;
import br.com.menthoros.backend.services.PlanoService;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Geração com temporada ACTIVE contra Postgres real (add-season-plan-load-enforcement, 5.1). A semana prevê
 * 620 TSS e longão de 20 km; o LLM (mockado) devolve um único treino fácil, sem LONGO. Atleta sem histórico
 * (CTL 0): o teto de rampa corta o alvo para 7 × 8 = 56.
 */
@DisplayName("plano semanal com enforcement da temporada")
class PlanoSemanaTemporadaEnforcementIT {

    private static final double TARGET_TSS = 620.0;

    abstract static class Cenario extends AbstractIntegrationTest {

        @Autowired protected PlanoService planoService;
        @Autowired protected PlanoReviewService planoReviewService;
        @Autowired protected MeterRegistry meterRegistry;
        @Autowired private EntityManager em;
        @Autowired private TransactionTemplate tx;
        @Autowired protected JdbcTemplate jdbc;

        @MockitoBean protected IaService iaService;

        protected Atleta atleta;

        @BeforeEach
        void preparar() {
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
                SeasonWeek semana = fx.semana(temporada, 2);
                semana.setTargetTss(TARGET_TSS);
                semana.setLongRunKm(BigDecimal.valueOf(20));
                fx.semana(temporada, 3);
            });
            TenantContext.setTenantId(atleta.getAssessoria().getId());
            when(iaService.geraPlanoSemanalAvancado(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(planoDeUmTreino());
        }

        @AfterEach
        void limpar() {
            TenantContext.clear();
        }

        protected WeekPlanSkeleton skeletonEnviadoAoLlm() {
            ArgumentCaptor<WeekPlanSkeleton> skeleton = ArgumentCaptor.forClass(WeekPlanSkeleton.class);
            verify(iaService).geraPlanoSemanalAvancado(any(), any(), any(), any(), any(), any(), any(),
                    skeleton.capture(), any());
            return skeleton.getValue();
        }

        protected double violacoes(String chave) {
            var contador = meterRegistry.find("planner.compliance.violation.count").tag("key", chave).counter();
            return contador == null ? 0.0 : contador.count();
        }

        protected Map<String, Object> colunasDoPlanner(PlanoSemanal plano) {
            return jdbc.queryForMap("SELECT planner_compliance_status, planner_requires_coach_review, "
                    + "planner_metadata_json::text AS metadata, review_status FROM tb_plano_semanal WHERE id = ?",
                    plano.getId());
        }

        private static PlanoSemanalLlmDto planoDeUmTreino() {
            TreinoPlanejadoLlmDto treino = new TreinoPlanejadoLlmDto(
                    "SEGUNDA", "FACIL", "130-140 bpm", 40, 1.0, 4,
                    "Base aeróbica", "45", 8.0, "5:40", List.of());
            return new PlanoSemanalLlmDto(8.0, 8.0, null, null, "PLANEJADO", "Semana de base", List.of(treino), List.of());
        }
    }

    @Nested
    @TestPropertySource(properties = {"season-plan.enabled=true", "onboarding.migrate-existing.enabled=false",
            "planner-engine.enabled=true", "planner-engine.shadow=false", "planner-engine.fail-open=true",
            "season-plan.enforcement.enabled=true"})
    @DisplayName("planner ligado + flag ligada")
    class FlagLigada extends Cenario {

        @Test
        @DisplayName("alvo cortado pela rampa, violação do longão no estágio 2, plano em revisão e delta na aprovação")
        void enforcaATemporada() {
            PlanoSemanal plano = planoService.gerarPlanoTreino(atleta.getId(), ModoGeracaoPlano.PROXIMA_SEMANA);

            WeekPlanSkeleton skeleton = skeletonEnviadoAoLlm();
            assertThat(skeleton.loadTarget().seasonTargetTss()).isEqualTo(TARGET_TSS);
            assertThat(skeleton.loadTarget().seasonCutReason()).isEqualTo(SeasonCutReason.TETO_RAMPA);
            assertThat(skeleton.loadTarget().targetTss()).isLessThan(TARGET_TSS);

            Map<String, Object> colunas = colunasDoPlanner(plano);
            assertThat(colunas.get("planner_compliance_status")).isEqualTo("FAILED");
            assertThat(colunas.get("planner_requires_coach_review")).isEqualTo(true);
            assertThat(violacoes("LONGAO_DIVERGENTE_DA_TEMPORADA")).isEqualTo(1.0);

            planoReviewService.aprovarPlano(plano.getId(), atleta.getAssessoria().getId());

            var delta = meterRegistry.find("season-plan.approved.deviation.abs.pct")
                    .tag("origem", "COACH").tag("direcao", "ABAIXO").summary();
            assertThat(delta).isNotNull();
            assertThat(delta.count()).isEqualTo(1);
        }
    }

    @Nested
    @TestPropertySource(properties = {"season-plan.enabled=true", "onboarding.migrate-existing.enabled=false",
            "planner-engine.enabled=true", "planner-engine.shadow=false", "planner-engine.fail-open=true",
            "season-plan.enforcement.enabled=false"})
    @DisplayName("planner ligado + flag desligada")
    class FlagDesligada extends Cenario {

        @Test
        @DisplayName("alvo legado e sem a checagem do longão (igual ao ticket 8)")
        void caminhoLegado() {
            planoService.gerarPlanoTreino(atleta.getId(), ModoGeracaoPlano.PROXIMA_SEMANA);

            WeekPlanSkeleton skeleton = skeletonEnviadoAoLlm();
            assertThat(skeleton.loadTarget().seasonTargetTss()).isNull();
            assertThat(skeleton.loadTarget().seasonCutReason()).isNull();
            assertThat(violacoes("LONGAO_DIVERGENTE_DA_TEMPORADA")).isZero();
        }
    }

    @Nested
    @TestPropertySource(properties = {"season-plan.enabled=true", "onboarding.migrate-existing.enabled=false",
            "planner-engine.enabled=false", "planner-engine.shadow=true",
            "season-plan.enforcement.enabled=true"})
    @DisplayName("só shadow + flag ligada")
    class SoShadow extends Cenario {

        @Test
        @DisplayName("a violação nova vira VIOLATIONS_DETECTED na auditoria, sem estágio 2")
        void auditaATemporada() {
            PlanoSemanal plano = planoService.gerarPlanoTreino(atleta.getId(), ModoGeracaoPlano.PROXIMA_SEMANA);

            Map<String, Object> colunas = colunasDoPlanner(plano);
            assertThat(colunas.get("planner_compliance_status")).isEqualTo("VIOLATIONS_DETECTED");
            assertThat((String) colunas.get("metadata")).contains("LONGAO_DIVERGENTE_DA_TEMPORADA");
            assertThat(violacoes("LONGAO_DIVERGENTE_DA_TEMPORADA")).as("estágio 2 não roda").isZero();
        }
    }
}
