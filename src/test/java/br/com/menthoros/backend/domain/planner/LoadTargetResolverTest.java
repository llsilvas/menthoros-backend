package br.com.menthoros.backend.domain.planner;

import java.util.List;

import br.com.menthoros.backend.dto.DecisaoProgressao;
import br.com.menthoros.backend.dto.ProgressaoHistoricoResumo;
import br.com.menthoros.backend.enums.EstadoProgressao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class LoadTargetResolverTest {

    private final LoadTargetResolver resolver = new LoadTargetResolver();

    @Nested
    @DisplayName("resolve — CA1: rampa de CTL nunca > 8 pontos/semana")
    class RampaDeCtl {

        @Test
        @DisplayName("CTL 40 com progressao agressiva sugerida nao ultrapassa o teto de rampa")
        void ctl40NaoUltrapassaTetoDeRampa() {
            DecisaoProgressao decisao = new DecisaoProgressao(EstadoProgressao.PROGREDIR, 1.0, 10, true, "progressao agressiva sugerida");
            ProgressaoHistoricoResumo historico = historicoComCtl(40.0, 0);

            WeeklyLoadTarget alvo = resolver.resolve(TrainingPhase.BUILD, decisao, historico);

            double rampaImplicada = alvo.targetTss() / 7 - 40.0;
            assertThat(rampaImplicada).isLessThanOrEqualTo(8.0);
        }

        @Test
        @DisplayName("PROGREDIR_LEVE tambem respeita o teto de rampa")
        void progredirLeveRespeitaTeto() {
            DecisaoProgressao decisao = new DecisaoProgressao(EstadoProgressao.PROGREDIR_LEVE, 0.8, 5, true, "leve");
            ProgressaoHistoricoResumo historico = historicoComCtl(40.0, 0);

            WeeklyLoadTarget alvo = resolver.resolve(TrainingPhase.BUILD, decisao, historico);

            double rampaImplicada = alvo.targetTss() / 7 - 40.0;
            assertThat(rampaImplicada).isLessThanOrEqualTo(8.0);
        }
    }

    @Nested
    @DisplayName("resolve — CA2: step-back na 4a semana consecutiva")
    class StepBack {

        @Test
        @DisplayName("4a semana consecutiva de progressao reduz o TSS-alvo entre 15% e 25%")
        void quartaSemanaReduz15a25Porcento() {
            DecisaoProgressao decisao = new DecisaoProgressao(EstadoProgressao.PROGREDIR, 0.0, 0, true, "progressao normal");
            ProgressaoHistoricoResumo semStepBack = historicoComCtl(50.0, 2);
            ProgressaoHistoricoResumo comStepBack = historicoComCtl(50.0, 3);

            WeeklyLoadTarget alvoBase = resolver.resolve(TrainingPhase.BUILD, decisao, semStepBack);
            WeeklyLoadTarget alvoStepBack = resolver.resolve(TrainingPhase.BUILD, decisao, comStepBack);

            double reducaoPercentual = 1 - (alvoStepBack.targetTss() / alvoBase.targetTss());
            assertThat(reducaoPercentual).isBetween(0.15, 0.25);
        }

        @Test
        @DisplayName("3a semana consecutiva (ainda nao a 4a) nao aplica step-back")
        void terceiraSemanaNaoAplicaStepBack() {
            DecisaoProgressao decisao = new DecisaoProgressao(EstadoProgressao.PROGREDIR, 0.0, 0, true, "progressao normal");
            ProgressaoHistoricoResumo historico = historicoComCtl(50.0, 1);

            WeeklyLoadTarget alvo = resolver.resolve(TrainingPhase.BUILD, decisao, historico);

            assertThat(alvo.targetTss()).isCloseTo(50.0 * 7, offset(0.01));
        }
    }

    @Nested
    @DisplayName("resolve — REDUZIR nunca vira aumento")
    class ReduzirNuncaViraAumento {

        @Test
        @DisplayName("mesmo com ajusteVolumePercentual positivo inconsistente, REDUZIR nao aumenta a carga")
        void reduzirNuncaAumentaMesmoComDadoInconsistente() {
            DecisaoProgressao decisao = new DecisaoProgressao(EstadoProgressao.REDUZIR, 0.10, -5, false, "fadiga acumulada");
            ProgressaoHistoricoResumo historico = historicoComCtl(50.0, 0);

            WeeklyLoadTarget alvo = resolver.resolve(TrainingPhase.BUILD, decisao, historico);

            assertThat(alvo.targetTss()).isLessThanOrEqualTo(50.0 * 7);
        }
    }

    @Nested
    @DisplayName("resolve — MANTER nao usa automaticamente todo o teto fisiologico")
    class ManterNaoUsaTetoTotal {

        @Test
        @DisplayName("MANTER com ajuste zero fica no baseline, nao no teto de rampa")
        void manterFicaNoBaselineNaoNoTeto() {
            DecisaoProgressao decisao = new DecisaoProgressao(EstadoProgressao.MANTER, 0.0, 0, false, "manutencao");
            ProgressaoHistoricoResumo historico = historicoComCtl(30.0, 0);

            WeeklyLoadTarget alvo = resolver.resolve(TrainingPhase.BUILD, decisao, historico);

            double baseline = 30.0 * 7;
            double tetoDeRampa = 7 * (30.0 + 8.0);
            assertThat(alvo.targetTss()).isCloseTo(baseline, offset(0.01));
            assertThat(alvo.targetTss()).isLessThan(tetoDeRampa);
        }
    }

    @Nested
    @DisplayName("resolve — historico insuficiente gera alvo conservador")
    class HistoricoInsuficiente {

        @Test
        @DisplayName("MANTER por historico insuficiente nao infla a carga acima do baseline")
        void historicoInsuficienteNaoInflaCarga() {
            DecisaoProgressao decisao = new DecisaoProgressao(EstadoProgressao.MANTER, 0.0, 0, false, "historico insuficiente");
            ProgressaoHistoricoResumo historico = historicoComCtl(20.0, 0);

            WeeklyLoadTarget alvo = resolver.resolve(TrainingPhase.BASE, decisao, historico);

            assertThat(alvo.targetTss()).isCloseTo(20.0 * 7, offset(0.01));
        }
    }

    @Nested
    @DisplayName("resolve — fases de contencao nunca aumentam alem do baseline")
    class FasesDeContencao {

        @Test
        @DisplayName("TAPER nao aumenta a carga mesmo com progressao sugerida")
        void taperNaoAumentaCarga() {
            DecisaoProgressao decisao = new DecisaoProgressao(EstadoProgressao.PROGREDIR, 0.5, 10, true, "progressao sugerida");
            ProgressaoHistoricoResumo historico = historicoComCtl(45.0, 0);

            WeeklyLoadTarget alvo = resolver.resolve(TrainingPhase.TAPER, decisao, historico);

            assertThat(alvo.targetTss()).isLessThanOrEqualTo(45.0 * 7);
        }

        @Test
        @DisplayName("RACE_WEEK nao aumenta a carga mesmo com progressao sugerida")
        void raceWeekNaoAumentaCarga() {
            DecisaoProgressao decisao = new DecisaoProgressao(EstadoProgressao.PROGREDIR, 0.5, 10, true, "progressao sugerida");
            ProgressaoHistoricoResumo historico = historicoComCtl(45.0, 0);

            WeeklyLoadTarget alvo = resolver.resolve(TrainingPhase.RACE_WEEK, decisao, historico);

            assertThat(alvo.targetTss()).isLessThanOrEqualTo(45.0 * 7);
        }
    }

    @Nested
    @DisplayName("resolve — regime cold-start (calibrationStage presente, ADR-0012)")
    class ColdStart {

        private DecisaoProgressao manter() {
            return new DecisaoProgressao(EstadoProgressao.MANTER, 0.0, 0, false, "cold-start");
        }

        @Test
        @DisplayName("OBSERVATION: min(ctlBaseline,40) x 7 x 0,60")
        void observation() {
            WeeklyLoadTarget alvo = resolver.resolve(
                    TrainingPhase.BASE, manter(), historicoComCtl(0.0, 0), CalibrationStage.OBSERVATION, 30.0);
            assertThat(alvo.targetTss()).isCloseTo(30 * 7 * 0.60, offset(0.01)); // 126
        }

        @Test
        @DisplayName("CALIBRATION: 30 x 7 x 0,75 = 157,5")
        void calibration() {
            WeeklyLoadTarget alvo = resolver.resolve(
                    TrainingPhase.BASE, manter(), historicoComCtl(0.0, 0), CalibrationStage.CALIBRATION, 30.0);
            assertThat(alvo.targetTss()).isCloseTo(157.5, offset(0.01));
        }

        @Test
        @DisplayName("STABILIZATION: 30 x 7 x 0,90 = 189")
        void stabilization() {
            WeeklyLoadTarget alvo = resolver.resolve(
                    TrainingPhase.BASE, manter(), historicoComCtl(0.0, 0), CalibrationStage.STABILIZATION, 30.0);
            assertThat(alvo.targetTss()).isCloseTo(189.0, offset(0.01));
        }

        @Test
        @DisplayName("cap do CTL: AVANCADO baseline 55 usa 40, nao 55")
        void capCtl() {
            WeeklyLoadTarget alvo = resolver.resolve(
                    TrainingPhase.BASE, manter(), historicoComCtl(0.0, 0), CalibrationStage.CALIBRATION, 55.0);
            assertThat(alvo.targetTss()).isCloseTo(40 * 7 * 0.75, offset(0.01)); // 210, nao 288,75
        }

        @Test
        @DisplayName("graduado (stage nulo): CTL de PMC, banda +-10%, sem rampa/cap")
        void graduadoCaminhoNormal() {
            WeeklyLoadTarget alvo = resolver.resolve(
                    TrainingPhase.BASE, manter(), historicoComCtl(45.0, 0), null, null);
            assertThat(alvo.targetTss()).isCloseTo(315.0, offset(0.01)); // 45 x 7
            assertThat(alvo.minTss()).isCloseTo(315.0 * 0.90, offset(0.01));
            assertThat(alvo.maxTss()).isCloseTo(315.0 * 1.10, offset(0.01));
        }

        @Test
        @DisplayName("piso 120 em fase progressiva quando o alvo rampado fica abaixo")
        void pisoEmFaseProgressiva() {
            WeeklyLoadTarget alvo = resolver.resolve(
                    TrainingPhase.BASE, manter(), historicoComCtl(0.0, 0), CalibrationStage.OBSERVATION, 10.0);
            assertThat(alvo.targetTss()).isCloseTo(120.0, offset(0.01)); // 42 -> piso 120
        }

        @Test
        @DisplayName("piso NAO se aplica em contencao (TAPER)")
        void pisoNaoSeAplicaEmContencao() {
            WeeklyLoadTarget alvo = resolver.resolve(
                    TrainingPhase.TAPER, manter(), historicoComCtl(0.0, 0), CalibrationStage.OBSERVATION, 10.0);
            assertThat(alvo.targetTss()).isCloseTo(42.0, offset(0.01)); // sem piso
        }

        @Test
        @DisplayName("banda +-25% no cold-start")
        void bandaColdStart() {
            WeeklyLoadTarget alvo = resolver.resolve(
                    TrainingPhase.BASE, manter(), historicoComCtl(0.0, 0), CalibrationStage.CALIBRATION, 30.0);
            assertThat(alvo.minTss()).isCloseTo(157.5 * 0.75, offset(0.01));
            assertThat(alvo.maxTss()).isCloseTo(157.5 * 1.25, offset(0.01));
        }

        @Test
        @DisplayName("cold-start lesionado empilha rampa e reducao: 40 x 7 x 0,90 x 0,5")
        void lesionadoEmpilha() {
            WeeklyLoadTarget alvo = resolver.resolve(
                    TrainingPhase.RECOVERY, manter(), historicoComCtl(0.0, 0), CalibrationStage.STABILIZATION, 40.0);
            assertThat(alvo.targetTss()).isCloseTo(40 * 7 * 0.90 * 0.5, offset(0.01)); // 126
        }
    }

    @Nested
    @DisplayName("resolve — RECOVERY/POST_RACE reduzem de verdade (caminho normal, ADR-0012)")
    class ReducaoContencao {

        @Test
        @DisplayName("RECOVERY: 0,5 x baseline")
        void recoveryReduz() {
            DecisaoProgressao decisao = new DecisaoProgressao(EstadoProgressao.MANTER, 0.0, 0, false, "recovery");
            WeeklyLoadTarget alvo = resolver.resolve(TrainingPhase.RECOVERY, decisao, historicoComCtl(40.0, 0));
            assertThat(alvo.targetTss()).isCloseTo(140.0, offset(0.01)); // 0,5 x 280
        }

        @Test
        @DisplayName("POST_RACE: 0,5 x baseline")
        void postRaceReduz() {
            DecisaoProgressao decisao = new DecisaoProgressao(EstadoProgressao.MANTER, 0.0, 0, false, "pos-prova");
            WeeklyLoadTarget alvo = resolver.resolve(TrainingPhase.POST_RACE, decisao, historicoComCtl(40.0, 0));
            assertThat(alvo.targetTss()).isCloseTo(140.0, offset(0.01));
        }

        @Test
        @DisplayName("TAPER nao sofre o fator 0,5 (segue min-cap do baseline)")
        void taperNaoReduz() {
            DecisaoProgressao decisao = new DecisaoProgressao(EstadoProgressao.MANTER, 0.0, 0, false, "taper");
            WeeklyLoadTarget alvo = resolver.resolve(TrainingPhase.TAPER, decisao, historicoComCtl(40.0, 0));
            assertThat(alvo.targetTss()).isCloseTo(280.0, offset(0.01)); // min(280,280)
        }
    }

    @Nested
    @DisplayName("resolveSeason — temporada como base e teto (add-season-plan-load-enforcement)")
    class CaminhoDaTemporada {

        private final DecisaoProgressao manter = new DecisaoProgressao(EstadoProgressao.MANTER, 0.0, 0, false, "manter");

        @Test
        @DisplayName("620 com CTL 70: o teto de rampa corta para 546 e o motivo fica registrado")
        void tetoDeRampaCorta() {
            WeeklyLoadTarget alvo = resolver.resolveSeason(TrainingPhase.BUILD, manter, historicoComCtl(70.0, 0),
                    null, null, 620.0);

            assertThat(alvo.targetTss()).isCloseTo(546.0, offset(0.01));
            assertThat(alvo.minTss()).isCloseTo(491.4, offset(0.01));
            assertThat(alvo.maxTss()).isCloseTo(600.6, offset(0.01));
            assertThat(alvo.seasonTargetTss()).isEqualTo(620.0);
            assertThat(alvo.seasonCutReason()).isEqualTo(SeasonCutReason.TETO_RAMPA);
        }

        @Test
        @DisplayName("taper/semana de prova: não passa da carga atual (ctl × 7), como no legado")
        void contencaoNaSemanaDeProva() {
            WeeklyLoadTarget taper = resolver.resolveSeason(TrainingPhase.TAPER, manter, historicoComCtl(40.0, 0),
                    null, null, 300.0);
            WeeklyLoadTarget build = resolver.resolveSeason(TrainingPhase.BUILD, manter, historicoComCtl(40.0, 0),
                    null, null, 300.0);

            assertThat(taper.targetTss()).isCloseTo(280.0, offset(0.01));
            assertThat(taper.seasonCutReason()).isEqualTo(SeasonCutReason.CONTENCAO_PROVA);
            assertThat(build.targetTss()).isEqualTo(300.0);
        }

        @Test
        @DisplayName("progressão pedindo aumento fica no valor da temporada")
        void progressaoNaoPassaDaTemporada() {
            DecisaoProgressao aumentar = new DecisaoProgressao(EstadoProgressao.PROGREDIR, 0.10, 5, true, "progredir");

            WeeklyLoadTarget alvo = resolver.resolveSeason(TrainingPhase.BUILD, aumentar, historicoComCtl(60.0, 0),
                    null, null, 400.0);

            assertThat(alvo.targetTss()).isEqualTo(400.0);
            assertThat(alvo.seasonCutReason()).isNull();
        }

        @Test
        @DisplayName("semana anterior perdida (CTL mais baixo, abaixo do teto) não gera compensação")
        void semanaPerdidaSemCompensacao() {
            WeeklyLoadTarget alvo = resolver.resolveSeason(TrainingPhase.BUILD, manter, historicoComCtl(60.0, 0),
                    null, null, 400.0);

            assertThat(alvo.targetTss()).isEqualTo(400.0);
        }

        @Test
        @DisplayName("quarta semana de progressão não aplica step-back: a descarga já está na temporada")
        void semStepBack() {
            DecisaoProgressao progredir = new DecisaoProgressao(EstadoProgressao.PROGREDIR, 0.0, 0, true, "progredir");

            WeeklyLoadTarget alvo = resolver.resolveSeason(TrainingPhase.BUILD, progredir, historicoComCtl(60.0, 3),
                    null, null, 400.0);

            assertThat(alvo.targetTss()).isEqualTo(400.0);
        }

        @Test
        @DisplayName("lesão (RECOVERY) e pós-prova reduzem pela metade a base da temporada")
        void reducaoPorFaseSobreABase() {
            for (TrainingPhase fase : List.of(TrainingPhase.RECOVERY, TrainingPhase.POST_RACE)) {
                WeeklyLoadTarget alvo = resolver.resolveSeason(fase, manter, historicoComCtl(60.0, 0),
                        null, null, 400.0);

                assertThat(alvo.targetTss()).isEqualTo(200.0);
                assertThat(alvo.seasonCutReason()).isEqualTo(SeasonCutReason.REDUCAO_FASE);
            }
        }

        @Test
        @DisplayName("estado REDUZIR corta pelo CTL atual com o ajuste negativo")
        void reduzirCorta() {
            DecisaoProgressao reduzir = new DecisaoProgressao(EstadoProgressao.REDUZIR, -0.20, 0, false, "reduzir");

            WeeklyLoadTarget alvo = resolver.resolveSeason(TrainingPhase.BUILD, reduzir, historicoComCtl(50.0, 0),
                    null, null, 400.0);

            assertThat(alvo.targetTss()).isCloseTo(280.0, offset(0.01));
            assertThat(alvo.seasonCutReason()).isEqualTo(SeasonCutReason.PROGRESSAO_REDUZIR);
        }

        @Test
        @DisplayName("cold-start: o piso de 120 não passa da temporada e a rampa de CTL 0 não se aplica")
        void coldStartPisoNaoPassaDaTemporada() {
            WeeklyLoadTarget alvo = resolver.resolveSeason(TrainingPhase.BUILD, manter, historicoComCtl(0.0, 0),
                    CalibrationStage.STABILIZATION, 10.0, 100.0);

            assertThat(alvo.targetTss()).isEqualTo(100.0);
            assertThat(alvo.minTss()).isCloseTo(75.0, offset(0.01));
            assertThat(alvo.maxTss()).isCloseTo(125.0, offset(0.01));
            assertThat(alvo.seasonCutReason()).isNull();
        }

        @Test
        @DisplayName("cold-start abaixo da temporada corta com o motivo COLD_START")
        void coldStartCorta() {
            WeeklyLoadTarget alvo = resolver.resolveSeason(TrainingPhase.BUILD, manter, historicoComCtl(0.0, 0),
                    CalibrationStage.CALIBRATION, 30.0, 300.0);

            assertThat(alvo.targetTss()).isCloseTo(157.5, offset(0.01));
            assertThat(alvo.seasonCutReason()).isEqualTo(SeasonCutReason.COLD_START);
        }

        @Test
        @DisplayName("o alvo resolvido nunca passa do targetTss, nem com CTL alto e progressão agressiva")
        void nuncaAcimaDaTemporada() {
            DecisaoProgressao agressiva = new DecisaoProgressao(EstadoProgressao.PROGREDIR, 1.0, 10, true, "agressiva");

            WeeklyLoadTarget alvo = resolver.resolveSeason(TrainingPhase.PEAK, agressiva, historicoComCtl(100.0, 0),
                    null, null, 200.0);

            assertThat(alvo.targetTss()).isEqualTo(200.0);
            assertThat(alvo.maxTss()).isCloseTo(220.0, offset(0.01));
        }
    }

    private ProgressaoHistoricoResumo historicoComCtl(double ctlAtual, int semanasProgressaoContinua) {
        return new ProgressaoHistoricoResumo(
                0, 0.0, 0.0, 0.0, 0, 0,
                null,
                0.0,
                ctlAtual,
                0.0,
                semanasProgressaoContinua,
                0, 0, 0, null
        );
    }
}
