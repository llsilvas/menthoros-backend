package br.com.menthoros.backend.domain.planner;

import br.com.menthoros.backend.dto.DecisaoProgressao;
import br.com.menthoros.backend.dto.ProgressaoHistoricoResumo;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.EstadoProgressao;
import br.com.menthoros.backend.enums.NivelExperiencia;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PlannerEngineTest {

    private final PlannerEngine engine = new PlannerEngine(
            new PeriodizationPlanner(),
            new LoadTargetResolver(),
            new TaperStrategy(),
            new InjuryRiskEvaluator(),
            new InjuryPolicyResolver(),
            new ConstraintValidator());

    private final LocalDate referencia = LocalDate.of(2026, 3, 9);

    @Nested
    @DisplayName("planWeek — FULL_CONTEXT (com OnboardingContext)")
    class FullContext {

        @Test
        @DisplayName("usa as constraints do OnboardingContext quando presente")
        void usaConstraintsDoOnboardingContext() {
            AthleteConstraints constraints = new AthleteConstraints(
                    List.of(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), 2, 90, List.of());
            OnboardingContext onboarding = new OnboardingContext(
                    new AthleteBaseline(45.0, referencia.minusDays(90)),
                    0.8,
                    new PlanningPolicy(ReviewMode.EXCEPTION_ONLY, 0.1, false),
                    constraints);

            PlannerInputSnapshot snapshot = snapshotBase(Optional.of(onboarding), false, null, null, -2.0, 45.0, 0);

            WeekPlanSkeleton skeleton = engine.planWeek(snapshot);

            assertThat(skeleton).isNotNull();
            assertThat(skeleton.loadTarget()).isNotNull();
        }
    }

    @Nested
    @DisplayName("planWeek — LEGACY_CONTEXT (sem OnboardingContext)")
    class LegacyContext {

        @Test
        @DisplayName("atleta legado sem OnboardingContext continua elegivel ao planner")
        void atletaLegadoContinuaElegivel() {
            PlannerInputSnapshot snapshot = snapshotBase(Optional.empty(), false, null, null, -2.0, 45.0, 0);

            WeekPlanSkeleton skeleton = engine.planWeek(snapshot);

            assertThat(skeleton).isNotNull();
            assertThat(skeleton.constraints().valid()).isTrue();
        }
    }

    @Nested
    @DisplayName("planWeek — historico insuficiente gera fallback conservador")
    class HistoricoInsuficiente {

        @Test
        @DisplayName("MANTER por historico insuficiente nao infla a carga acima do baseline")
        void historicoInsuficienteNaoInflaCarga() {
            DecisaoProgressao decisao = new DecisaoProgressao(EstadoProgressao.MANTER, 0.0, 0, false, "historico insuficiente");
            PlannerInputSnapshot snapshot = snapshotComDecisao(decisao, false, null, null, -2.0, 20.0, 0);

            WeekPlanSkeleton skeleton = engine.planWeek(snapshot);

            assertThat(skeleton.loadTarget().targetTss()).isCloseTo(20.0 * 7, org.assertj.core.data.Offset.offset(0.01));
        }
    }

    @Nested
    @DisplayName("planWeek — requiresCoachReview")
    class RequiresCoachReview {

        @Test
        @DisplayName("TSB abaixo de -30 forca requiresCoachReview")
        void tsbBaixoForcaReview() {
            PlannerInputSnapshot snapshot = snapshotBase(Optional.empty(), false, null, null, -35.0, 45.0, 0);

            WeekPlanSkeleton skeleton = engine.planWeek(snapshot);

            assertThat(skeleton.requiresCoachReview()).isTrue();
        }

        @Test
        @DisplayName("lesao ativa forca requiresCoachReview e fase RECOVERY")
        void lesaoAtivaForcaReviewEFaseRecovery() {
            PlannerInputSnapshot snapshot = snapshotBase(Optional.empty(), true, null, null, -2.0, 45.0, 0);

            WeekPlanSkeleton skeleton = engine.planWeek(snapshot);

            assertThat(skeleton.requiresCoachReview()).isTrue();
            assertThat(skeleton.phase()).isEqualTo(TrainingPhase.RECOVERY);
        }

        @Test
        @DisplayName("sem sinais de risco, nao forca review")
        void semSinaisDeRiscoNaoForcaReview() {
            PlannerInputSnapshot snapshot = snapshotBase(Optional.empty(), false, null, null, -2.0, 45.0, 0);

            WeekPlanSkeleton skeleton = engine.planWeek(snapshot);

            assertThat(skeleton.requiresCoachReview()).isFalse();
        }
    }

    @Nested
    @DisplayName("planWeek — determinismo (CA17)")
    class Determinismo {

        @Test
        @DisplayName("mesmo snapshot produz sempre o mesmo skeleton")
        void mesmoSnapshotProduzMesmoSkeleton() {
            PlannerInputSnapshot snapshot = snapshotBase(Optional.empty(), false, null, null, -2.0, 45.0, 0);

            WeekPlanSkeleton primeiraChamada = engine.planWeek(snapshot);
            WeekPlanSkeleton segundaChamada = engine.planWeek(snapshot);

            assertThat(primeiraChamada).isEqualTo(segundaChamada);
        }
    }

    private PlannerInputSnapshot snapshotBase(Optional<OnboardingContext> onboardingContext,
                                               boolean temLesao,
                                               String descricaoLesao,
                                               LocalDate dataUltimaLesao,
                                               double tsbAtual,
                                               double ctlAtual,
                                               int semanasProgressaoContinua) {
        DecisaoProgressao decisao = new DecisaoProgressao(EstadoProgressao.MANTER, 0.0, 0, false, "progressao normal");
        return snapshotComDecisaoEHistorico(decisao, onboardingContext, temLesao, descricaoLesao, dataUltimaLesao,
                tsbAtual, ctlAtual, semanasProgressaoContinua);
    }

    private PlannerInputSnapshot snapshotComDecisao(DecisaoProgressao decisao,
                                                     boolean temLesao,
                                                     String descricaoLesao,
                                                     LocalDate dataUltimaLesao,
                                                     double tsbAtual,
                                                     double ctlAtual,
                                                     int semanasProgressaoContinua) {
        return snapshotComDecisaoEHistorico(decisao, Optional.empty(), temLesao, descricaoLesao, dataUltimaLesao,
                tsbAtual, ctlAtual, semanasProgressaoContinua);
    }

    private PlannerInputSnapshot snapshotComDecisaoEHistorico(DecisaoProgressao decisao,
                                                               Optional<OnboardingContext> onboardingContext,
                                                               boolean temLesao,
                                                               String descricaoLesao,
                                                               LocalDate dataUltimaLesao,
                                                               double tsbAtual,
                                                               double ctlAtual,
                                                               int semanasProgressaoContinua) {
        AthleteSnapshot athlete = new AthleteSnapshot(
                UUID.randomUUID(), NivelExperiencia.INTERMEDIARIO, temLesao, descricaoLesao, dataUltimaLesao,
                List.of(DiaSemana.SEGUNDA, DiaSemana.QUINTA), null);

        ProgressaoHistoricoResumo historico = new ProgressaoHistoricoResumo(
                0, 0, 0.0, 0.0, 0.0, 0, 0, null, tsbAtual, ctlAtual, 0.0, semanasProgressaoContinua);

        return new PlannerInputSnapshot(
                athlete, decisao, historico, List.of(), List.of(), onboardingContext, referencia, 30);
    }
}
