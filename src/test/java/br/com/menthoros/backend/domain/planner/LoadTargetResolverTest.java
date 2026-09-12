package br.com.menthoros.backend.domain.planner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import br.com.menthoros.backend.dto.DecisaoProgressao;
import br.com.menthoros.backend.dto.ProgressaoHistoricoResumo;
import br.com.menthoros.backend.enums.EstadoProgressao;

class LoadTargetResolverTest {

    private final LoadTargetResolver resolver = new LoadTargetResolver();

    @Nested
    @DisplayName("resolve")
    class Resolve {

        @Test
        @DisplayName("cold-start OBSERVATION: min(ctlBaseline,40) x 7 x 0,60")
        void coldStartObservation() {
            WeeklyLoadTarget alvo = resolver.resolve(
                    TrainingPhase.BASE, manter(), historico(null), CalibrationStage.OBSERVATION, 30.0);

            assertThat(alvo.targetTss()).isCloseTo(30 * 7 * 0.60, within(0.01)); // 126
        }

        @Test
        @DisplayName("cold-start CALIBRATION: 30 x 7 x 0,75 = 157,5")
        void coldStartCalibration() {
            WeeklyLoadTarget alvo = resolver.resolve(
                    TrainingPhase.BASE, manter(), historico(null), CalibrationStage.CALIBRATION, 30.0);

            assertThat(alvo.targetTss()).isCloseTo(157.5, within(0.01));
        }

        @Test
        @DisplayName("cold-start STABILIZATION: 30 x 7 x 0,90 = 189")
        void coldStartStabilization() {
            WeeklyLoadTarget alvo = resolver.resolve(
                    TrainingPhase.BASE, manter(), historico(null), CalibrationStage.STABILIZATION, 30.0);

            assertThat(alvo.targetTss()).isCloseTo(189.0, within(0.01));
        }

        @Test
        @DisplayName("cap do CTL: AVANCADO com baseline 55 usa 40, nao 55")
        void coldStartCapCtl() {
            WeeklyLoadTarget alvo = resolver.resolve(
                    TrainingPhase.BASE, manter(), historico(null), CalibrationStage.CALIBRATION, 55.0);

            assertThat(alvo.targetTss()).isCloseTo(40 * 7 * 0.75, within(0.01)); // 210, nao 288,75
        }

        @Test
        @DisplayName("graduado (stage nulo): usa CTL de PMC, banda +-10%, sem rampa/cap")
        void graduadoCaminhoNormal() {
            WeeklyLoadTarget alvo = resolver.resolve(
                    TrainingPhase.BASE, manter(), historico(45.0), null, null);

            assertThat(alvo.targetTss()).isCloseTo(315.0, within(0.01)); // 45 x 7
            assertThat(alvo.minTss()).isCloseTo(315.0 * 0.90, within(0.01));
            assertThat(alvo.maxTss()).isCloseTo(315.0 * 1.10, within(0.01));
        }

        @Test
        @DisplayName("piso 120 em fase progressiva quando o alvo rampado fica abaixo")
        void pisoEmFaseProgressiva() {
            WeeklyLoadTarget alvo = resolver.resolve(
                    TrainingPhase.BASE, manter(), historico(null), CalibrationStage.OBSERVATION, 10.0);

            // 10 x 7 x 0,60 = 42 -> elevado ao piso 120
            assertThat(alvo.targetTss()).isCloseTo(120.0, within(0.01));
        }

        @Test
        @DisplayName("piso NAO se aplica em fase de contencao (TAPER)")
        void pisoNaoSeAplicaEmContencao() {
            WeeklyLoadTarget alvo = resolver.resolve(
                    TrainingPhase.TAPER, manter(), historico(null), CalibrationStage.OBSERVATION, 10.0);

            assertThat(alvo.targetTss()).isCloseTo(42.0, within(0.01)); // sem piso
        }

        @Test
        @DisplayName("banda +-25% no cold-start")
        void bandaColdStart() {
            WeeklyLoadTarget alvo = resolver.resolve(
                    TrainingPhase.BASE, manter(), historico(null), CalibrationStage.CALIBRATION, 30.0);

            assertThat(alvo.minTss()).isCloseTo(157.5 * 0.75, within(0.01));
            assertThat(alvo.maxTss()).isCloseTo(157.5 * 1.25, within(0.01));
        }
    }

    private DecisaoProgressao manter() {
        return new DecisaoProgressao(EstadoProgressao.MANTER, 0.0, 0, false, "cold-start");
    }

    private ProgressaoHistoricoResumo historico(Double ctlAtual) {
        return new ProgressaoHistoricoResumo(0, 0, 0.0, 0.0, 0.0, 0, 0, null, 0.0, ctlAtual, 0.0, 0);
    }
}
