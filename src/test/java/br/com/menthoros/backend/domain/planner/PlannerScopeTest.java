package br.com.menthoros.backend.domain.planner;

import br.com.menthoros.backend.dto.DecisaoProgressao;
import br.com.menthoros.backend.dto.ProgressaoHistoricoResumo;
import br.com.menthoros.backend.enums.EstadoProgressao;
import br.com.menthoros.backend.enums.NivelExperiencia;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

/**
 * Escopo v1 running-first (design.md Decisao 13, CA13): sem CTL/ATL por esporte, sem
 * distribuicao de sessoes por modalidade — TSS agregado segue como guardrail conservador de
 * fadiga geral para qualquer modalidade.
 */
class PlannerScopeTest {

    private final PlannerEngine engine = new PlannerEngine(
            new PeriodizationPlanner(),
            new LoadTargetResolver(),
            new TaperStrategy(),
            new InjuryRiskEvaluator(),
            new InjuryPolicyResolver(),
            new ConstraintValidator());

    private final LocalDate referencia = LocalDate.of(2026, 3, 9);

    @Nested
    @DisplayName("plannerScope — deteccao de modalidade nao-running")
    class DeteccaoDeModalidade {

        @Test
        @DisplayName("modalidade nao-running registra plannerScope=RUNNING_FIRST no metadata")
        void modalidadeNaoRunningRegistraEscopo() {
            WeekPlanSkeleton skeleton = engine.planWeek(snapshot("CICLISMO"));

            assertThat(skeleton.plannerScope()).isEqualTo("RUNNING_FIRST");
        }

        @Test
        @DisplayName("modalidade RUNNING nao registra plannerScope")
        void modalidadeRunningNaoRegistraEscopo() {
            WeekPlanSkeleton skeleton = engine.planWeek(snapshot("RUNNING"));

            assertThat(skeleton.plannerScope()).isNull();
        }

        @Test
        @DisplayName("modalidade ausente (atleta legado) nao registra plannerScope")
        void modalidadeAusenteNaoRegistraEscopo() {
            WeekPlanSkeleton skeleton = engine.planWeek(snapshot(null));

            assertThat(skeleton.plannerScope()).isNull();
        }
    }

    @Nested
    @DisplayName("plannerScope — guardrail conservador de TSS agregado")
    class GuardrailDeTssAgregado {

        @Test
        @DisplayName("modalidade nao-running usa o mesmo calculo de TSS agregado, sem CTL/ATL por esporte")
        void naoRunningUsaMesmoCalculoAgregado() {
            WeekPlanSkeleton skeletonRunning = engine.planWeek(snapshot("RUNNING"));
            WeekPlanSkeleton skeletonMultisport = engine.planWeek(snapshot("CICLISMO"));

            assertThat(skeletonMultisport.loadTarget().targetTss())
                    .isCloseTo(skeletonRunning.loadTarget().targetTss(), offset(0.01));
        }
    }

    private PlannerInputSnapshot snapshot(String modalidade) {
        AthleteSnapshot athlete = new AthleteSnapshot(
                UUID.randomUUID(), NivelExperiencia.INTERMEDIARIO, false, null, null, List.of(), modalidade);
        DecisaoProgressao decisao = new DecisaoProgressao(EstadoProgressao.MANTER, 0.0, 0, false, "progressao normal");
        ProgressaoHistoricoResumo historico = new ProgressaoHistoricoResumo(
                0, 0, 0.0, 0.0, 0.0, 0, 0, null, -2.0, 45.0, 0.0, 0);

        return new PlannerInputSnapshot(
                athlete, decisao, historico, List.of(), List.of(), Optional.empty(), referencia, 30);
    }
}
