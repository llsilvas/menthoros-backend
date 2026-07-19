package br.com.menthoros.backend.domain.planner;

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

    private ProgressaoHistoricoResumo historicoComCtl(double ctlAtual, int semanasProgressaoContinua) {
        return new ProgressaoHistoricoResumo(
                0, 0, 0.0, 0.0, 0.0, 0, 0,
                null,
                0.0,
                ctlAtual,
                0.0,
                semanasProgressaoContinua
        );
    }
}
