package br.com.menthoros.backend.domain.planner;

import br.com.menthoros.backend.dto.DecisaoProgressao;
import br.com.menthoros.backend.dto.ProgressaoHistoricoResumo;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.EstadoProgressao;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.TipoTreino;
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
                    constraints,
                    null);

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

    /**
     * fix-planner-ignora-prova-na-semana (task 4.1, CA8): a lista de provas da semana e por data real
     * dentro de [referenceDate, referenceDate+6] — a determinante pode estar meses a frente e nao
     * pode virar ancora nem isencao projetando so o DayOfWeek dela na semana corrente.
     */
    @Nested
    @DisplayName("planWeek — provas da semana (por data real)")
    class ProvasDaSemana {

        private final ProvaSnapshot determinanteLonge = new ProvaSnapshot(referencia.plusDays(90), 42.2, true, false);
        private final ProvaSnapshot preparatoriaDomingo = new ProvaSnapshot(referencia.plusDays(6), 21.1, false, false);

        @Test
        @DisplayName("determinante daqui a 3 meses e nenhuma preparatoria: lista vazia")
        void determinanteForaDaSemana() {
            WeekPlanSkeleton skeleton = engine.planWeek(snapshotComProvas(List.of(determinanteLonge)));

            assertThat(skeleton.provasDaSemana()).isEmpty();
            assertThat(skeleton.provaDeterminante()).contains(determinanteLonge);
        }

        @Test
        @DisplayName("preparatoria no domingo da semana entra; a determinante longe nao")
        void preparatoriaNaSemana() {
            WeekPlanSkeleton skeleton = engine.planWeek(snapshotComProvas(List.of(determinanteLonge, preparatoriaDomingo)));

            assertThat(skeleton.provasDaSemana()).containsExactly(preparatoriaDomingo);
        }

        @Test
        @DisplayName("determinante dentro da semana entra na lista")
        void determinanteNaSemana() {
            ProvaSnapshot alvoDomingo = new ProvaSnapshot(referencia.plusDays(6), 21.1, true, false);

            WeekPlanSkeleton skeleton = engine.planWeek(snapshotComProvas(List.of(alvoDomingo)));

            assertThat(skeleton.provasDaSemana()).containsExactly(alvoDomingo);
        }

        @Test
        @DisplayName("task 5.1/5.3: preparatória na semana (fase PEAK) ganha slot PROVA ancorado, sem LONGO no D-1")
        void preparatoriaGanhaSlotAncoradoSemLongoNoD1() {
            // atleta SEGUNDA/QUINTA/SABADO (prova no domingo NAO e dia configurado); determinante PEAK longe
            AthleteSnapshot athlete = new AthleteSnapshot(UUID.randomUUID(), NivelExperiencia.INTERMEDIARIO, false, null, null,
                    List.of(DiaSemana.SEGUNDA, DiaSemana.QUINTA, DiaSemana.SABADO), null);
            DecisaoProgressao decisao = new DecisaoProgressao(EstadoProgressao.MANTER, 0.0, 0, false, "teste");
            ProgressaoHistoricoResumo historico = new ProgressaoHistoricoResumo(
                    0, 0.0, 0.0, 0.0, 0, 0, null, -2.0, 45.0, 0.0, 0, 0, 0, 0, null);
            ProvaSnapshot determinantePeak = new ProvaSnapshot(referencia.plusDays(30), 10.0, true, false);
            PlannerInputSnapshot snapshot = new PlannerInputSnapshot(athlete, decisao, historico,
                    List.of(determinantePeak, preparatoriaDomingo), List.of(), Optional.empty(), referencia, 30);

            WeekPlanSkeleton skeleton = engine.planWeek(snapshot);

            assertThat(skeleton.phase()).isEqualTo(TrainingPhase.PEAK);
            assertThat(skeleton.sessions()).anyMatch(s -> "PROVA".equals(s.sessionType()) && s.day() == DayOfWeek.SUNDAY);
            assertThat(skeleton.sessions()).noneMatch(s -> s.day() == DayOfWeek.SATURDAY
                    && (("LONGO".equals(s.sessionType())) || SessionDayAllocator.DURAS.contains(TipoTreino.valueOf(s.sessionType()))));
        }

        @Test
        @DisplayName("CA9/5.3: uma prova da semana nunca deixa sessão com dia nulo — slot pesado inviável some, nunca fica \"a definir\"")
        void nenhumaSessaoFicaComDiaNulo() {
            // atleta SEGUNDA/QUARTA/QUINTA/SABADO; prova no domingo (fora dos dias configurados)
            AthleteSnapshot athlete = new AthleteSnapshot(UUID.randomUUID(), NivelExperiencia.INTERMEDIARIO, false, null, null,
                    List.of(DiaSemana.SEGUNDA, DiaSemana.QUARTA, DiaSemana.QUINTA, DiaSemana.SABADO), null);
            DecisaoProgressao decisao = new DecisaoProgressao(EstadoProgressao.MANTER, 0.0, 0, false, "teste");
            ProgressaoHistoricoResumo historico = new ProgressaoHistoricoResumo(
                    0, 0.0, 0.0, 0.0, 0, 0, null, -2.0, 45.0, 0.0, 0, 0, 0, 0, null);
            PlannerInputSnapshot snapshot = new PlannerInputSnapshot(athlete, decisao, historico,
                    List.of(determinanteLonge, preparatoriaDomingo), List.of(), Optional.empty(), referencia, 30);

            WeekPlanSkeleton skeleton = engine.planWeek(snapshot);

            assertThat(skeleton.phase()).isEqualTo(TrainingPhase.BUILD);
            // Nenhum slot chega ao prompt sem dia — ou foi alocado, ou o PlannerEngine o removeu (5.3).
            assertThat(skeleton.sessions()).noneMatch(s -> s.day() == null);
            // Nenhuma sessão pesada (LONGO/dura) na véspera da prova (sábado).
            assertThat(skeleton.sessions()).noneMatch(s -> s.day() == DayOfWeek.SATURDAY
                    && (s.sessionType().equals("LONGO") || SessionDayAllocator.DURAS.contains(TipoTreino.valueOf(s.sessionType()))));
            if (skeleton.requiresCoachReview()) {
                assertThat(skeleton.coachReviewReason()).contains("sem dia elegivel");
            }
        }

        /**
         * Achado do /qa (code-reviewer): com duas sessões pesadas órfãs na mesma semana, o motivo
         * da primeira era perdido (variável simples sobrescrita). Testa o helper direto — forçar
         * duas órfãs de verdade via composição/alocação end-to-end é frágil (depende da matemática
         * de polarização); aqui o que importa é a lógica de acumulação em si.
         */
        @Test
        @DisplayName("/qa: duas sessões pesadas órfãs na mesma semana — o motivo acumula as duas, não só a última")
        void duasOrfasAcumulamOMotivo() {
            SessionSlot longoOrfao = new SessionSlot(null, "LONGO", 90.0, "Zona 2", true, 90);
            SessionSlot duraOrfa = new SessionSlot(null, "INTERVALADO", 70.0, "Zona 5", false, 60);
            SessionSlot leve = new SessionSlot(DayOfWeek.MONDAY, "FACIL", 40.0, "Zona 1", false, 30);

            var resultado = PlannerEngine.removerPesadasSemDia(
                    List.of(longoOrfao, duraOrfa, leve), List.of(preparatoriaDomingo));

            assertThat(resultado.sessoes()).containsExactly(leve); // as duas órfãs saem, a leve fica
            assertThat(resultado.motivo()).contains("LONGO").contains("INTERVALADO");
        }

        @Test
        @DisplayName("/qa: sem órfãs, motivo é null")
        void semOrfasMotivoNulo() {
            SessionSlot leve = new SessionSlot(DayOfWeek.MONDAY, "FACIL", 40.0, "Zona 1", false, 30);

            var resultado = PlannerEngine.removerPesadasSemDia(List.of(leve), List.of());

            assertThat(resultado.sessoes()).containsExactly(leve);
            assertThat(resultado.motivo()).isNull();
        }

        @Test
        @DisplayName("CA8: sessao no dia da prova da semana nao e DIA_INDISPONIVEL mesmo fora dos dias do atleta")
        void sessaoNoDiaDaProvaNaoEIndisponivel() {
            // atleta SEGUNDA/QUINTA (snapshot base); prova alvo no domingo -> RACE_WEEK ancora PROVA no domingo
            ProvaSnapshot alvoDomingo = new ProvaSnapshot(referencia.plusDays(6), 21.1, true, false);

            WeekPlanSkeleton skeleton = engine.planWeek(snapshotComProvas(List.of(alvoDomingo)));

            assertThat(skeleton.sessions()).anyMatch(s -> s.day() == DayOfWeek.SUNDAY);
            assertThat(skeleton.constraints().violations())
                    .noneMatch(v -> v.key() == ConstraintViolationKey.DIA_INDISPONIVEL);
        }
    }

    private PlannerInputSnapshot snapshotComProvas(List<ProvaSnapshot> provas) {
        PlannerInputSnapshot base = snapshotBase(Optional.empty(), false, null, null, -2.0, 45.0, 0);
        return new PlannerInputSnapshot(base.athlete(), base.decisaoProgressao(), base.progressaoHistorico(),
                provas, base.historico(), base.onboardingContext(), base.referenceDate(), base.injuryRecentWindowDays());
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
                0, 0.0, 0.0, 0.0, 0, 0, null, tsbAtual, ctlAtual, 0.0, semanasProgressaoContinua, 0, 0, 0, null);

        return new PlannerInputSnapshot(
                athlete, decisao, historico, List.of(), List.of(), onboardingContext, referencia, 30);
    }
}
