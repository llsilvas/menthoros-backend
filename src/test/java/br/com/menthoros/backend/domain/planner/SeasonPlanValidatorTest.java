package br.com.menthoros.backend.domain.planner;

import br.com.menthoros.backend.domain.planner.SeasonPlanValidator.Alert;
import br.com.menthoros.backend.domain.planner.SeasonPlanValidator.Rule;
import br.com.menthoros.backend.domain.planner.SeasonPlanner.WeekPlan;
import br.com.menthoros.backend.enums.DistanciaProva;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static br.com.menthoros.backend.domain.planner.TrainingPhase.BASE;
import static br.com.menthoros.backend.domain.planner.TrainingPhase.BUILD;
import static br.com.menthoros.backend.domain.planner.TrainingPhase.PEAK;
import static br.com.menthoros.backend.domain.planner.TrainingPhase.RACE_WEEK;
import static br.com.menthoros.backend.domain.planner.TrainingPhase.TAPER;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SeasonPlanValidator")
class SeasonPlanValidatorTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 16);
    /** Domingo da semana 3 a partir de HOJE: a RACE_WEEK começa 6 dias antes. */
    private static final LocalDate PROVA = HOJE.plusWeeks(2).plusDays(6);

    @Nested
    @DisplayName("validate")
    class Validate {

        @Test
        @DisplayName("a proposta default de 16 semanas não tem alerta")
        void propostaDefaultSemAlerta() {
            LocalDate prova = HOJE.plusWeeks(15).plusDays(6);
            List<WeekPlan> semanas = SeasonPlanner.plan(DistanciaProva.KM_42, null, prova, HOJE, 40.0,
                    new BigDecimal("40")).weeks();

            assertThat(SeasonPlanValidator.validate(semanas, prova, new BigDecimal("42.2"))).isEmpty();
        }

        @Test
        @DisplayName("RAMP: volume acima de 10% sobre a semana anterior dispara; 10% exatos não")
        void rampa() {
            List<Alert> dispara = SeasonPlanValidator.validate(
                    List.of(semana(1, BASE, "40", false), semana(2, BASE, "44.1", false)), PROVA, null);
            List<Alert> limite = SeasonPlanValidator.validate(
                    List.of(semana(1, BASE, "40", false), semana(2, BASE, "44", false)), PROVA, null);

            assertThat(dispara).extracting(Alert::rule, Alert::weekIndex).containsExactly(
                    org.assertj.core.groups.Tuple.tuple(Rule.RAMP, 2));
            assertThat(limite).isEmpty();
        }

        @Test
        @DisplayName("RAMP: não conta a volta da descarga nem semana anterior com volume zero")
        void rampaIgnoraDescargaEZero() {
            assertThat(SeasonPlanValidator.validate(
                    List.of(semana(1, BASE, "32", true), semana(2, BASE, "44", false)), PROVA, null)).isEmpty();
            assertThat(SeasonPlanValidator.validate(
                    List.of(semana(1, BASE, "0", false), semana(2, BASE, "44", false)), PROVA, null)).isEmpty();
        }

        @Test
        @DisplayName("LONG_RUN_SHARE: 34 km de longão em 72 km dispara; 30% exatos não")
        void longaoConcentrado() {
            List<Alert> dispara = SeasonPlanValidator.validate(
                    List.of(comLongao(semana(1, BASE, "72", false), "34")), PROVA, null);
            List<Alert> limite = SeasonPlanValidator.validate(
                    List.of(comLongao(semana(1, BASE, "72", false), "21.6")), PROVA, null);

            assertThat(dispara).extracting(Alert::rule).containsExactly(Rule.LONG_RUN_SHARE);
            assertThat(dispara.get(0).measured()).isCloseTo(47.2, org.assertj.core.data.Offset.offset(0.1));
            assertThat(limite).isEmpty();
        }

        @Test
        @DisplayName("DELOAD_CADENCE: 5 semanas seguidas de Base/Construção sem descarga dispara na 5ª; 4 não")
        void cadenciaDeDescarga() {
            List<WeekPlan> cinco = new ArrayList<>();
            for (int i = 1; i <= 5; i++) {
                cinco.add(semana(i, i <= 3 ? BASE : BUILD, "40", false));
            }

            assertThat(SeasonPlanValidator.validate(cinco, PROVA, null))
                    .extracting(Alert::rule, Alert::weekIndex)
                    .containsExactly(org.assertj.core.groups.Tuple.tuple(Rule.DELOAD_CADENCE, 5));
            assertThat(SeasonPlanValidator.validate(cinco.subList(0, 4), PROVA, null)).isEmpty();
        }

        @Test
        @DisplayName("TAPER_DEPTH: redução 15 p.p. longe da curva dispara; 9 p.p. não")
        void profundidadeDoTaper() {
            // RACE_WEEK a 6 dias: a curva pede 55,5% de redução sobre o pico 364
            List<Alert> dispara = SeasonPlanValidator.validate(List.of(
                    semana(1, PEAK, "52", false, 364), semana(2, TAPER, "40", false, 300),
                    semana(3, RACE_WEEK, "30", false, 364 * 0.596)), PROVA, null);
            List<Alert> perto = SeasonPlanValidator.validate(List.of(
                    semana(1, PEAK, "52", false, 364), semana(2, TAPER, "40", false, 196),
                    semana(3, RACE_WEEK, "30", false, 364 * 0.535)), PROVA, null);

            assertThat(dispara).extracting(Alert::rule, Alert::weekIndex).contains(
                    org.assertj.core.groups.Tuple.tuple(Rule.TAPER_DEPTH, 3));
            assertThat(perto).extracting(Alert::weekIndex).doesNotContain(3);
        }
    }

    @Nested
    @DisplayName("validate — polimento")
    class Polimento {

        @Test
        @DisplayName("TAPER_DEPTH: diferença de exatamente 10 p.p. não dispara (folga de ponto flutuante)")
        void igualdadeNoLimiteNaoDispara() {
            // TAPER a 13 dias da prova: a curva pede ~46,2%; 159,549... sobre 364 dá 56,2%, 10 p.p. exatos
            LocalDate prova = HOJE.plusWeeks(1).plusDays(13);
            List<Alert> alertas = SeasonPlanValidator.validate(List.of(
                    semana(1, PEAK, "52", false, 364), semana(2, TAPER, "40", false, 159.54930524368655)),
                    prova, null);

            assertThat(alertas).isEmpty();
        }

        @Test
        @DisplayName("TAPER_DEPTH: polimento da maratona a 27 dias da prova está fora da janela de 21; a 20 não")
        void janelaDaDistancia() {
            BigDecimal maratona = new BigDecimal("42.2");
            List<Alert> fora = SeasonPlanValidator.validate(
                    List.of(semana(1, TAPER, "40", false)), HOJE.plusDays(27), maratona);
            List<Alert> dentro = SeasonPlanValidator.validate(
                    List.of(semana(1, TAPER, "40", false)), HOJE.plusDays(20), maratona);
            List<Alert> semDistancia = SeasonPlanValidator.validate(
                    List.of(semana(1, TAPER, "40", false)), HOJE.plusDays(27), null);

            assertThat(fora).extracting(Alert::rule, Alert::measured, Alert::limit).containsExactly(
                    org.assertj.core.groups.Tuple.tuple(Rule.TAPER_DEPTH, 27.0, 21.0));
            assertThat(dentro).isEmpty();
            assertThat(semDistancia).isEmpty();
        }
    }

    private static WeekPlan semana(int indice, TrainingPhase fase, String km, boolean deload) {
        return semana(indice, fase, km, deload, 300);
    }

    private static WeekPlan semana(int indice, TrainingPhase fase, String km, boolean deload, double tss) {
        return new WeekPlan(indice, HOJE.plusWeeks(indice - 1L), fase, new BigDecimal(km), tss, deload, null, null);
    }

    private static WeekPlan comLongao(WeekPlan w, String longao) {
        return new WeekPlan(w.weekIndex(), w.weekStart(), w.phase(), w.targetVolumeKm(), w.targetTss(),
                w.deload(), new BigDecimal(longao), null);
    }
}
