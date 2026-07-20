package br.com.menthoros.backend.domain.planner;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PeriodizationPlannerTest {

    private final PeriodizationPlanner planner = new PeriodizationPlanner();
    private final LocalDate referencia = LocalDate.of(2026, 3, 2); // segunda-feira

    @Nested
    @DisplayName("resolvePhase — selecao de prova")
    class SelecaoDeProva {

        @Test
        @DisplayName("prova-alvo explicita vence mesmo havendo prova mais proxima")
        void provaAlvoExplicitaVenceSempre() {
            ProvaSnapshot proxima = new ProvaSnapshot(referencia.plusDays(10), 10.0, false, false);
            ProvaSnapshot alvo = new ProvaSnapshot(referencia.plusDays(90), 21.0975, true, false);

            PeriodizationResult resultado = planner.resolvePhase(List.of(proxima, alvo), referencia);

            assertThat(resultado.provaDeterminante()).contains(alvo);
        }

        @Test
        @DisplayName("sem prova-alvo, seleciona a mais proxima por data")
        void semAlvoSelecionaMaisProximaPorData() {
            ProvaSnapshot maisProxima = new ProvaSnapshot(referencia.plusDays(20), 10.0, false, false);
            ProvaSnapshot maisDistante = new ProvaSnapshot(referencia.plusDays(60), 21.0975, false, false);

            PeriodizationResult resultado = planner.resolvePhase(List.of(maisDistante, maisProxima), referencia);

            assertThat(resultado.provaDeterminante()).contains(maisProxima);
        }

        @Test
        @DisplayName("empate na mesma semana desempata por maior distancia")
        void empateNaMesmaSemanaDesempataPorDistancia() {
            ProvaSnapshot curta = new ProvaSnapshot(referencia.plusDays(30), 10.0, false, false);
            ProvaSnapshot longa = new ProvaSnapshot(referencia.plusDays(32), 21.0975, false, false);

            PeriodizationResult resultado = planner.resolvePhase(List.of(curta, longa), referencia);

            assertThat(resultado.provaDeterminante()).contains(longa);
        }

        @Test
        @DisplayName("ignora provas canceladas na selecao")
        void ignoraProvasCanceladas() {
            ProvaSnapshot cancelada = new ProvaSnapshot(referencia.plusDays(5), 10.0, false, true);
            ProvaSnapshot valida = new ProvaSnapshot(referencia.plusDays(60), 21.0975, false, false);

            PeriodizationResult resultado = planner.resolvePhase(List.of(cancelada, valida), referencia);

            assertThat(resultado.provaDeterminante()).contains(valida);
        }

        @Test
        @DisplayName("ignora provas passadas na selecao do alvo (fora da janela pos-prova)")
        void ignoraProvasPassadasForaDaJanelaPosProva() {
            ProvaSnapshot passadaAntiga = new ProvaSnapshot(referencia.minusDays(30), 10.0, false, false);
            ProvaSnapshot futura = new ProvaSnapshot(referencia.plusDays(60), 21.0975, false, false);

            PeriodizationResult resultado = planner.resolvePhase(List.of(passadaAntiga, futura), referencia);

            assertThat(resultado.provaDeterminante()).contains(futura);
        }
    }

    @Nested
    @DisplayName("resolvePhase — prova preparatoria na semana")
    class ProvaPreparatoriaNaSemana {

        @Test
        @DisplayName("prova preparatoria dentro da semana aparece como constraint estrutural, sem alterar a fase macro")
        void provaPreparatoriaAparaceceSemAlterarFaseMacro() {
            ProvaSnapshot alvo = new ProvaSnapshot(referencia.plusDays(100), 21.0975, true, false);
            ProvaSnapshot preparatoria = new ProvaSnapshot(referencia.plusDays(3), 5.0, false, false);

            PeriodizationResult resultado = planner.resolvePhase(List.of(alvo, preparatoria), referencia);

            assertThat(resultado.phase()).isEqualTo(TrainingPhase.BASE); // fase macro definida pela prova-alvo (100 dias)
            assertThat(resultado.provasPreparatoriasNaSemana()).containsExactly(preparatoria);
        }

        @Test
        @DisplayName("prova fora da semana nao aparece como preparatoria")
        void provaForaDaSemanaNaoAparece() {
            ProvaSnapshot alvo = new ProvaSnapshot(referencia.plusDays(90), 21.0975, true, false);
            ProvaSnapshot foraDaSemana = new ProvaSnapshot(referencia.plusDays(20), 5.0, false, false);

            PeriodizationResult resultado = planner.resolvePhase(List.of(alvo, foraDaSemana), referencia);

            assertThat(resultado.provasPreparatoriasNaSemana()).isEmpty();
        }
    }

    @Nested
    @DisplayName("resolvePhase — fase por dias faltando")
    class FasePorDiasFaltando {

        @Test
        @DisplayName("sem nenhuma prova cadastrada, fase e BASE (desenvolvimento geral)")
        void semProvaFaseBase() {
            PeriodizationResult resultado = planner.resolvePhase(List.of(), referencia);

            assertThat(resultado.phase()).isEqualTo(TrainingPhase.BASE);
            assertThat(resultado.provaDeterminante()).isEmpty();
        }

        @Test
        @DisplayName("mais de 12 semanas para a prova -> BASE")
        void maisDe12SemanasBase() {
            ProvaSnapshot alvo = new ProvaSnapshot(referencia.plusDays(100), 21.0975, true, false);

            PeriodizationResult resultado = planner.resolvePhase(List.of(alvo), referencia);

            assertThat(resultado.phase()).isEqualTo(TrainingPhase.BASE);
        }

        @Test
        @DisplayName("entre 9 e 12 semanas -> BUILD")
        void entre9e12SemanasBuild() {
            ProvaSnapshot alvo = new ProvaSnapshot(referencia.plusDays(70), 21.0975, true, false);

            PeriodizationResult resultado = planner.resolvePhase(List.of(alvo), referencia);

            assertThat(resultado.phase()).isEqualTo(TrainingPhase.BUILD);
        }

        @Test
        @DisplayName("entre 4 e 8 semanas -> PEAK (fase especifica)")
        void entre4e8SemanasPeak() {
            ProvaSnapshot alvo = new ProvaSnapshot(referencia.plusDays(42), 21.0975, true, false);

            PeriodizationResult resultado = planner.resolvePhase(List.of(alvo), referencia);

            assertThat(resultado.phase()).isEqualTo(TrainingPhase.PEAK);
        }

        @Test
        @DisplayName("entre 2 e 3 semanas -> TAPER")
        void entre2e3SemanasTaper() {
            ProvaSnapshot alvo = new ProvaSnapshot(referencia.plusDays(18), 21.0975, true, false);

            PeriodizationResult resultado = planner.resolvePhase(List.of(alvo), referencia);

            assertThat(resultado.phase()).isEqualTo(TrainingPhase.TAPER);
        }

        @Test
        @DisplayName("prova dentro da propria semana (0-6 dias, no futuro) -> RACE_WEEK")
        void provaDentroDaSemanaRaceWeek() {
            ProvaSnapshot alvo = new ProvaSnapshot(referencia.plusDays(3), 21.0975, true, false);

            PeriodizationResult resultado = planner.resolvePhase(List.of(alvo), referencia);

            assertThat(resultado.phase()).isEqualTo(TrainingPhase.RACE_WEEK);
        }

        @Test
        @DisplayName("prova no proprio dia de referencia -> RACE_WEEK")
        void provaHojeRaceWeek() {
            ProvaSnapshot alvo = new ProvaSnapshot(referencia, 21.0975, true, false);

            PeriodizationResult resultado = planner.resolvePhase(List.of(alvo), referencia);

            assertThat(resultado.phase()).isEqualTo(TrainingPhase.RACE_WEEK);
        }

        @Test
        @DisplayName("semana imediatamente apos a prova -> POST_RACE, nao volta direto para BUILD")
        void semanaPosProvaNaoVoltaDireitoParaBuild() {
            ProvaSnapshot recemRealizada = new ProvaSnapshot(referencia.minusDays(2), 21.0975, true, false);
            ProvaSnapshot proximoAlvoDistante = new ProvaSnapshot(referencia.plusDays(70), 21.0975, false, false);

            PeriodizationResult resultado = planner.resolvePhase(List.of(recemRealizada, proximoAlvoDistante), referencia);

            assertThat(resultado.phase()).isEqualTo(TrainingPhase.POST_RACE);
        }
    }
}
