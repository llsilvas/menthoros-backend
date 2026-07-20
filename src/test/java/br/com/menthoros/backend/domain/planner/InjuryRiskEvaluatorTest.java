package br.com.menthoros.backend.domain.planner;

import br.com.menthoros.backend.dto.ProgressaoHistoricoResumo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InjuryRiskEvaluatorTest {

    private final InjuryRiskEvaluator evaluator = new InjuryRiskEvaluator();
    private final LocalDate referencia = LocalDate.of(2026, 3, 9);

    @Nested
    @DisplayName("assess — faixas de TSB (design.md Decisao 15)")
    class FaixasDeTsb {

        @Test
        @DisplayName("TSB > -10 e zona segura, sem forcar review")
        void tsbAcimaDeMenos10EhSeguro() {
            ProgressaoHistoricoResumo historico = historicoComTsb(-5.0);

            InjuryRiskAssessment resultado = evaluator.assess(historico, historicoVariado(), referencia);

            assertThat(resultado.level()).isEqualTo(InjuryRiskLevel.SAFE);
            assertThat(resultado.requiresCoachReview()).isFalse();
        }

        @Test
        @DisplayName("TSB entre -30 e -10 e WARNING")
        void tsbEntreMenos30EMenos10EhWarning() {
            ProgressaoHistoricoResumo historico = historicoComTsb(-20.0);

            InjuryRiskAssessment resultado = evaluator.assess(historico, historicoVariado(), referencia);

            assertThat(resultado.level()).isEqualTo(InjuryRiskLevel.WARNING);
        }

        @Test
        @DisplayName("TSB < -30 e HIGH_RISK e forca requiresCoachReview")
        void tsbAbaixoDeMenos30EhHighRiskEForcaReview() {
            ProgressaoHistoricoResumo historico = historicoComTsb(-35.0);

            InjuryRiskAssessment resultado = evaluator.assess(historico, historicoVariado(), referencia);

            assertThat(resultado.level()).isEqualTo(InjuryRiskLevel.HIGH_RISK);
            assertThat(resultado.requiresCoachReview()).isTrue();
        }
    }

    @Nested
    @DisplayName("assess — monotonia como sinal secundario")
    class Monotonia {

        @Test
        @DisplayName("carga identica todos os dias da semana (monotonia > 2.0) eleva SAFE para WARNING")
        void cargaConstanteElevaSafeParaWarning() {
            ProgressaoHistoricoResumo historico = historicoComTsb(-2.0); // seria SAFE por TSB isoladamente
            List<TreinoRealizadoSnapshot> cargaConstante = List.of(
                    new TreinoRealizadoSnapshot(referencia.minusDays(1), 50),
                    new TreinoRealizadoSnapshot(referencia.minusDays(2), 50),
                    new TreinoRealizadoSnapshot(referencia.minusDays(3), 50),
                    new TreinoRealizadoSnapshot(referencia.minusDays(4), 50),
                    new TreinoRealizadoSnapshot(referencia.minusDays(5), 50),
                    new TreinoRealizadoSnapshot(referencia.minusDays(6), 50),
                    new TreinoRealizadoSnapshot(referencia.minusDays(7), 50)
            );

            InjuryRiskAssessment resultado = evaluator.assess(historico, cargaConstante, referencia);

            assertThat(resultado.level()).isEqualTo(InjuryRiskLevel.WARNING);
        }

        @Test
        @DisplayName("carga variada (baixa monotonia) nao eleva o nivel de risco")
        void cargaVariadaNaoElevaRisco() {
            ProgressaoHistoricoResumo historico = historicoComTsb(-2.0);

            InjuryRiskAssessment resultado = evaluator.assess(historico, historicoVariado(), referencia);

            assertThat(resultado.level()).isEqualTo(InjuryRiskLevel.SAFE);
        }

        @Test
        @DisplayName("sem historico de treino (repouso total), monotonia nao dispara falso positivo")
        void semHistoricoNaoDisparaFalsoPositivo() {
            ProgressaoHistoricoResumo historico = historicoComTsb(-2.0);

            InjuryRiskAssessment resultado = evaluator.assess(historico, List.of(), referencia);

            assertThat(resultado.level()).isEqualTo(InjuryRiskLevel.SAFE);
        }
    }

    private ProgressaoHistoricoResumo historicoComTsb(double tsbAtual) {
        return new ProgressaoHistoricoResumo(0, 0, 0.0, 0.0, 0.0, 0, 0, null, tsbAtual, 40.0, 0.0, 0);
    }

    private List<TreinoRealizadoSnapshot> historicoVariado() {
        return List.of(
                new TreinoRealizadoSnapshot(referencia.minusDays(1), 0),
                new TreinoRealizadoSnapshot(referencia.minusDays(2), 50),
                new TreinoRealizadoSnapshot(referencia.minusDays(3), 100),
                new TreinoRealizadoSnapshot(referencia.minusDays(4), 20),
                new TreinoRealizadoSnapshot(referencia.minusDays(5), 80),
                new TreinoRealizadoSnapshot(referencia.minusDays(6), 0),
                new TreinoRealizadoSnapshot(referencia.minusDays(7), 60)
        );
    }
}
