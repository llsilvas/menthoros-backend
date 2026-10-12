package br.com.menthoros.backend.domain.compliance;

import br.com.menthoros.backend.domain.planner.AthleteConstraints;
import br.com.menthoros.backend.domain.planner.ConstraintValidationResult;
import br.com.menthoros.backend.domain.planner.InjuryRiskAssessment;
import br.com.menthoros.backend.domain.planner.InjuryRiskLevel;
import br.com.menthoros.backend.domain.planner.ProvaSnapshot;
import br.com.menthoros.backend.domain.planner.TrainingPhase;
import br.com.menthoros.backend.domain.planner.WeekPlanSkeleton;
import br.com.menthoros.backend.domain.planner.WeeklyLoadTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SkeletonComplianceCheckerTest {

    private final SkeletonComplianceChecker checker = new SkeletonComplianceChecker();
    private final LocalDate referencia = LocalDate.of(2026, 3, 9); // segunda-feira

    /**
     * fix-planner-ignora-prova-na-semana (task 4.3, CA8): sessao na data real de uma prova da semana
     * nao e dia indisponivel — a prova de domingo do atleta que nao treina aos domingos era o 1o
     * item de violacao do plano real. A isencao e pela data, nao pelo tipo: PROVA em data errada
     * continua violacao.
     */
    @Nested
    @DisplayName("dia de prova da semana (CA8)")
    class DiaDeProva {

        private final ProvaSnapshot provaDomingo = new ProvaSnapshot(referencia.plusDays(6), 21.1, false, false);
        private final AthleteConstraints segundaASabado = new AthleteConstraints(
                List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
                        DayOfWeek.FRIDAY, DayOfWeek.SATURDAY), null, null, List.of());

        @Test
        @DisplayName("pre: PROVA no domingo (data da prova) com atleta sem domingo nao e CONSTRAINT_DURA_VIOLADA")
        void preProvaNaDataIsenta() {
            var violacoes = checker.checkPreRedistribution(
                    plano(sessao(0, "FACIL", 40, "Z2"), sessao(6, "PROVA", 100, "Z4")),
                    skeleton(TrainingPhase.PEAK, 300, 270, 330), contexto(provaDomingo));

            assertThat(violacoes).noneMatch(v -> v.key() == PlannerViolationKey.CONSTRAINT_DURA_VIOLADA);
        }

        @Test
        @DisplayName("post: treino no domingo (data da prova) com atleta sem domingo nao e DIA_INDISPONIVEL")
        void postProvaNaDataIsenta() {
            var violacoes = checker.checkPostRedistribution(
                    plano(sessao(0, "FACIL", 40, "Z2"), sessao(6, "PROVA", 100, "Z4")),
                    skeleton(TrainingPhase.PEAK, 300, 270, 330), contexto(provaDomingo));

            assertThat(violacoes).noneMatch(v -> v.key() == PlannerViolationKey.DIA_INDISPONIVEL);
        }

        @Test
        @DisplayName("PROVA em data errada (sabado, prova e domingo) com atleta sem sabado continua violacao")
        void provaEmDataErradaContinuaViolacao() {
            AthleteConstraints semSabadoNemDomingo = new AthleteConstraints(
                    List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), null, null, List.of());

            var violacoes = checker.checkPreRedistribution(
                    plano(sessao(0, "FACIL", 40, "Z2"), sessao(5, "PROVA", 100, "Z4")),
                    skeleton(TrainingPhase.PEAK, 300, 270, 330),
                    new ComplianceContext(Optional.empty(), semSabadoNemDomingo, referencia, List.of(provaDomingo)));

            assertThat(violacoes).anyMatch(v -> v.key() == PlannerViolationKey.CONSTRAINT_DURA_VIOLADA);
        }

        @Test
        @DisplayName("determinante fora da semana (provasDaSemana vazia) nao isenta o domingo")
        void determinanteForaDaSemanaNaoIsenta() {
            ProvaSnapshot determinanteLonge = new ProvaSnapshot(referencia.plusDays(70), 42.2, true, false);

            var violacoes = checker.checkPostRedistribution(
                    plano(sessao(6, "FACIL", 40, "Z2")),
                    skeleton(TrainingPhase.BUILD, 300, 270, 330),
                    new ComplianceContext(Optional.of(determinanteLonge), segundaASabado, referencia, List.of()));

            assertThat(violacoes).anyMatch(v -> v.key() == PlannerViolationKey.DIA_INDISPONIVEL);
        }

        private ComplianceContext contexto(ProvaSnapshot provaDaSemana) {
            return new ComplianceContext(Optional.empty(), segundaASabado, referencia, List.of(provaDaSemana));
        }
    }

    @Nested
    @DisplayName("checkPreRedistribution")
    class CheckPreRedistribution {

        @Test
        @DisplayName("TSS total fora da faixa +-10% do alvo gera TSS_FORA_DA_FAIXA")
        void tssForaDaFaixaGeraViolacao() {
            WeekPlanSkeleton skeleton = skeleton(TrainingPhase.BUILD, 300.0, 270.0, 330.0);
            GeneratedPlanSnapshot plano = plano(sessao(0, "REGENERATIVO", 500, "Z1")); // muito acima da faixa

            // TSS_FORA_DA_FAIXA e soft (estagio 2) desde a calibracao 2026-09-11: nao entra no estagio 1.
            assertThat(checker.checkPreRedistribution(plano, skeleton, contexto(Optional.empty())))
                    .noneMatch(v -> v.key() == PlannerViolationKey.TSS_FORA_DA_FAIXA);
            assertThat(checker.checkPostRedistribution(plano, skeleton, contexto(Optional.empty())))
                    .anyMatch(v -> v.key() == PlannerViolationKey.TSS_FORA_DA_FAIXA);
        }

        @Test
        @DisplayName("TSS total dentro da faixa nao gera violacao de TSS")
        void tssDentroDaFaixaNaoGeraViolacao() {
            WeekPlanSkeleton skeleton = skeleton(TrainingPhase.BUILD, 300.0, 270.0, 330.0);
            GeneratedPlanSnapshot plano = plano(
                    sessao(1, "REGENERATIVO", 100, "Z1"),
                    sessao(3, "INTERVALADO", 90, "Z4"),
                    sessao(5, "LONGO", 110, "Z2"));

            List<PlannerViolation> violacoes = checker.checkPostRedistribution(plano, skeleton, contexto(Optional.empty()));

            assertThat(violacoes).noneMatch(v -> v.key() == PlannerViolationKey.TSS_FORA_DA_FAIXA);
        }

        @Test
        @DisplayName("plano sem sessoes mas com alvo de carga positivo gera SESSION_COUNT_DIVERGENTE")
        void planoVazioComAlvoPositivoGeraViolacao() {
            WeekPlanSkeleton skeleton = skeleton(TrainingPhase.BUILD, 300.0, 270.0, 330.0);
            GeneratedPlanSnapshot plano = plano();

            List<PlannerViolation> violacoes = checker.checkPreRedistribution(plano, skeleton, contexto(Optional.empty()));

            assertThat(violacoes).anyMatch(v -> v.key() == PlannerViolationKey.SESSION_COUNT_DIVERGENTE);
        }

        @Test
        @DisplayName("sessao LONGO acima de 40% do alvo semanal gera LONGO_ACIMA_TETO")
        void longoAcimaDoTetoGeraViolacao() {
            WeekPlanSkeleton skeleton = skeleton(TrainingPhase.BUILD, 300.0, 270.0, 330.0);
            GeneratedPlanSnapshot plano = plano(sessao(5, "LONGO", 200, "Z2")); // > 40% de 300

            // LONGO_ACIMA_TETO e soft (estagio 2) desde a calibracao 2026-09-11: nao entra no estagio 1.
            assertThat(checker.checkPreRedistribution(plano, skeleton, contexto(Optional.empty())))
                    .noneMatch(v -> v.key() == PlannerViolationKey.LONGO_ACIMA_TETO);
            assertThat(checker.checkPostRedistribution(plano, skeleton, contexto(Optional.empty())))
                    .anyMatch(v -> v.key() == PlannerViolationKey.LONGO_ACIMA_TETO);
        }

        @Test
        @DisplayName("sessao INTERVALADO acima de 25% do alvo semanal gera EXCESSO_INTENSIDADE")
        void intervaladoExcessivoGeraViolacao() {
            // teto = max(300 x 0.40, 60) = 120; EXCESSO_INTENSIDADE agora e check do estagio 2 (POST),
            // nao mais do estagio 1 (calibracao 2026-09-11): soft/review, nunca 422 no retry.
            WeekPlanSkeleton skeleton = skeleton(TrainingPhase.BUILD, 300.0, 270.0, 330.0);
            GeneratedPlanSnapshot plano = plano(sessao(2, "INTERVALADO", 140, "Z4")); // > 40% de 300

            List<PlannerViolation> violacoes = checker.checkPostRedistribution(plano, skeleton, contexto(Optional.empty()));

            assertThat(violacoes).anyMatch(v -> v.key() == PlannerViolationKey.EXCESSO_INTENSIDADE);
        }

        @Test
        @DisplayName("EXCESSO_INTENSIDADE nao entra no estagio 1 (calibracao 2026-09-11): so no estagio 2")
        void excessoIntensidadeNaoEntraNoEstagio1() {
            WeekPlanSkeleton skeleton = skeleton(TrainingPhase.BUILD, 300.0, 270.0, 330.0);
            GeneratedPlanSnapshot plano = plano(sessao(2, "INTERVALADO", 140, "Z4"));

            List<PlannerViolation> pre = checker.checkPreRedistribution(plano, skeleton, contexto(Optional.empty()));

            assertThat(pre).noneMatch(v -> v.key() == PlannerViolationKey.EXCESSO_INTENSIDADE);
        }

        @Test
        @DisplayName("piso de 60 TSS: intervalado real em semana leve NAO viola (evita 422 espurio)")
        void intervaladoRealEmSemanaLevePassa() {
            // targetTss 120: sem piso, teto seria 120 x 0.40 = 48 e um intervalado real (60) reprovaria.
            // Com o piso, teto = max(48, 60) = 60 → 60 nao excede 60.
            WeekPlanSkeleton skeleton = skeleton(TrainingPhase.BUILD, 120.0, 108.0, 132.0);
            GeneratedPlanSnapshot plano = plano(sessao(2, "INTERVALADO", 60, "Z4"));

            List<PlannerViolation> violacoes = checker.checkPostRedistribution(plano, skeleton, contexto(Optional.empty()));

            assertThat(violacoes).noneMatch(v -> v.key() == PlannerViolationKey.EXCESSO_INTENSIDADE);
        }

        @Test
        @DisplayName("fase RECOVERY com sessao intervalada gera FASE_DIVERGENTE")
        void intervaladoDuranteRecoveryGeraViolacao() {
            WeekPlanSkeleton skeleton = skeleton(TrainingPhase.RECOVERY, 100.0, 90.0, 110.0);
            GeneratedPlanSnapshot plano = plano(sessao(2, "INTERVALADO", 30, "Z4"));

            List<PlannerViolation> violacoes = checker.checkPreRedistribution(plano, skeleton, contexto(Optional.empty()));

            assertThat(violacoes).anyMatch(v -> v.key() == PlannerViolationKey.FASE_DIVERGENTE);
        }

        @Test
        @DisplayName("sessao pesada 48-72h antes da prova (posicao gerada) gera SESSAO_PESADA_PROXIMA_PROVA")
        void sessaoPesadaPertoDaProvaGeraViolacao() {
            WeekPlanSkeleton skeleton = skeleton(TrainingPhase.TAPER, 200.0, 180.0, 220.0);
            ProvaSnapshot prova = new ProvaSnapshot(referencia.plusDays(10), 21.0975, true, false);
            GeneratedPlanSnapshot plano = plano(sessao(7, "INTERVALADO", 80, "Z4")); // 2 dias antes da prova (dia 8)

            List<PlannerViolation> violacoes = checker.checkPreRedistribution(plano, skeleton, contexto(Optional.of(prova)));

            assertThat(violacoes).anyMatch(v -> v.key() == PlannerViolationKey.SESSAO_PESADA_PROXIMA_PROVA);
        }

        @Test
        @DisplayName("sessao em dia fora das constraints duras do atleta gera CONSTRAINT_DURA_VIOLADA")
        void sessaoForaDoDiaDisponivelGeraViolacao() {
            WeekPlanSkeleton skeleton = skeleton(TrainingPhase.BUILD, 300.0, 270.0, 330.0);
            AthleteConstraints constraints = new AthleteConstraints(List.of(DayOfWeek.MONDAY), null, null, List.of());
            GeneratedPlanSnapshot plano = plano(sessao(1, "REGENERATIVO", 90, "Z1")); // terca — nao disponivel

            List<PlannerViolation> violacoes = checker.checkPreRedistribution(
                    plano, skeleton, new ComplianceContext(Optional.empty(), constraints, referencia));

            assertThat(violacoes).anyMatch(v -> v.key() == PlannerViolationKey.CONSTRAINT_DURA_VIOLADA);
        }
    }

    @Nested
    @DisplayName("checkPostRedistribution")
    class CheckPostRedistribution {

        @Test
        @DisplayName("treino redistribuido para dia indisponivel gera DIA_INDISPONIVEL")
        void diaIndisponivelGeraViolacao() {
            WeekPlanSkeleton skeleton = skeleton(TrainingPhase.BUILD, 300.0, 270.0, 330.0);
            AthleteConstraints constraints = new AthleteConstraints(List.of(DayOfWeek.MONDAY), null, null, List.of());
            GeneratedPlanSnapshot redistribuido = plano(sessao(6, "LONGO", 100, "Z2")); // domingo

            List<PlannerViolation> violacoes = checker.checkPostRedistribution(
                    redistribuido, skeleton, new ComplianceContext(Optional.empty(), constraints, referencia));

            assertThat(violacoes).anyMatch(v -> v.key() == PlannerViolationKey.DIA_INDISPONIVEL);
        }

        @Test
        @DisplayName("sessao pesada perto da prova apos reposicionamento gera SESSAO_PESADA_PROXIMA_PROVA")
        void sessaoPesadaPertoDaProvaAposReposicionamentoGeraViolacao() {
            WeekPlanSkeleton skeleton = skeleton(TrainingPhase.TAPER, 200.0, 180.0, 220.0);
            ProvaSnapshot prova = new ProvaSnapshot(referencia.plusDays(10), 21.0975, true, false);
            GeneratedPlanSnapshot redistribuido = plano(sessao(8, "INTERVALADO", 80, "Z4")); // 2 dias antes

            List<PlannerViolation> violacoes = checker.checkPostRedistribution(
                    redistribuido, skeleton, contexto(Optional.of(prova)));

            assertThat(violacoes).anyMatch(v -> v.key() == PlannerViolationKey.SESSAO_PESADA_PROXIMA_PROVA);
        }

        @Test
        @DisplayName("TSS redistribuido acima do teto durante TAPER gera TAPER_VIOLADO")
        void tssAcimaDoTetoDuranteTaperGeraViolacao() {
            WeekPlanSkeleton skeleton = skeleton(TrainingPhase.TAPER, 150.0, 135.0, 165.0);
            GeneratedPlanSnapshot redistribuido = plano(sessao(1, "LONGO", 250, "Z2"));

            List<PlannerViolation> violacoes = checker.checkPostRedistribution(
                    redistribuido, skeleton, contexto(Optional.empty()));

            assertThat(violacoes).anyMatch(v -> v.key() == PlannerViolationKey.TAPER_VIOLADO);
        }

        @Test
        @DisplayName("fora de TAPER/RACE_WEEK, TSS acima do teto anterior nao gera TAPER_VIOLADO")
        void foraDeTaperNaoAvaliaTaperViolado() {
            WeekPlanSkeleton skeleton = skeleton(TrainingPhase.BUILD, 300.0, 270.0, 330.0);
            GeneratedPlanSnapshot redistribuido = plano(sessao(1, "LONGO", 500, "Z2"));

            List<PlannerViolation> violacoes = checker.checkPostRedistribution(
                    redistribuido, skeleton, contexto(Optional.empty()));

            assertThat(violacoes).noneMatch(v -> v.key() == PlannerViolationKey.TAPER_VIOLADO);
        }
    }

    private WeekPlanSkeleton skeleton(TrainingPhase phase, double targetTss, double minTss, double maxTss) {
        return new WeekPlanSkeleton(
                phase,
                new WeeklyLoadTarget(targetTss, minTss, maxTss, "teste"),
                List.of(),
                new InjuryRiskAssessment(InjuryRiskLevel.SAFE, false, null),
                new ConstraintValidationResult(true, List.of()),
                false,
                null,
                referencia,
                null,
                Optional.empty());
    }

    private ComplianceContext contexto(Optional<ProvaSnapshot> provaDeterminante) {
        AthleteConstraints constraints = new AthleteConstraints(
                List.of(DayOfWeek.values()), null, null, List.of());
        return new ComplianceContext(provaDeterminante, constraints, referencia);
    }

    private GeneratedPlanSnapshot plano(GeneratedSessionSnapshot... sessoes) {
        return new GeneratedPlanSnapshot(List.of(sessoes));
    }

    private GeneratedSessionSnapshot sessao(int diasAposReferencia, String tipo, int tss, String zona) {
        return new GeneratedSessionSnapshot(referencia.plusDays(diasAposReferencia), tipo, tss, zona);
    }
}
