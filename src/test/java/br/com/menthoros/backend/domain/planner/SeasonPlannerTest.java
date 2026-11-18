package br.com.menthoros.backend.domain.planner;

import br.com.menthoros.backend.domain.planner.SeasonPlanner.SeasonPlan;
import br.com.menthoros.backend.domain.planner.SeasonPlanner.WeekPlan;
import br.com.menthoros.backend.enums.DistanciaProva;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static br.com.menthoros.backend.domain.planner.TrainingPhase.BASE;
import static br.com.menthoros.backend.domain.planner.TrainingPhase.BUILD;
import static br.com.menthoros.backend.domain.planner.TrainingPhase.PEAK;
import static br.com.menthoros.backend.domain.planner.TrainingPhase.RACE_WEEK;
import static br.com.menthoros.backend.domain.planner.TrainingPhase.TAPER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SeasonPlanner")
class SeasonPlannerTest {

    /** Segunda-feira — o exemplo canônico do proposal (D3). */
    private static final LocalDate HOJE = LocalDate.of(2026, 11, 16);
    private static final BigDecimal VOLUME = new BigDecimal("40.0");

    @Nested
    @DisplayName("totalWeeks")
    class TotalWeeks {

        @ParameterizedTest(name = "hoje {0}, prova {1} → {2} semanas")
        @CsvSource({
                "2026-11-16, 2027-04-04, 20",
                "2026-11-18, 2027-04-04, 20",
                "2026-11-22, 2027-04-04, 20",
                "2026-11-16, 2027-03-28, 19",
                "2026-11-16, 2026-11-22, 1",
                "2026-11-16, 2026-11-23, 2"
        })
        @DisplayName("conta de segunda a segunda, semana da prova inclusa")
        void contaSemanas(LocalDate hoje, LocalDate prova, int esperado) {
            assertThat(SeasonPlanner.totalWeeks(prova, hoje)).isEqualTo(esperado);
        }

        @Test
        @DisplayName("prova passada dá zero")
        void provaPassada() {
            assertThat(SeasonPlanner.totalWeeks(HOJE.minusDays(1), HOJE)).isZero();
        }
    }

    @Nested
    @DisplayName("plan")
    class Plan {

        @Test
        @DisplayName("KM_42 a 20 semanas com CTL 40: Base ganha as semanas extras e a curva bate o exemplo")
        void maratonaVinteSemanas() {
            SeasonPlan plano = plan(DistanciaProva.KM_42, null, LocalDate.of(2027, 4, 4), 40.0);

            assertThat(contagem(plano)).containsExactlyInAnyOrderEntriesOf(
                    Map.of(BASE, 10L, BUILD, 4L, PEAK, 3L, TAPER, 2L, RACE_WEEK, 1L));
            assertThat(plano.shortPreparation()).isFalse();
            List<WeekPlan> semanas = plano.weeks();
            assertThat(semanas).hasSize(20);
            assertThat(semanas.get(0).weekIndex()).isEqualTo(1);
            assertThat(semanas.get(0).weekStart()).isEqualTo(HOJE);
            assertThat(semanas.get(19).weekStart()).isEqualTo(LocalDate.of(2027, 3, 29));
            assertThat(semanas.get(0).targetTss()).isEqualTo(285.0);
            assertThat(semanas.get(16).phase()).isEqualTo(PEAK);
            assertThat(semanas.get(16).targetTss()).isEqualTo(364.0);
            assertThat(semanas.get(17).targetTss()).isEqualTo(218.0);
            assertThat(semanas.get(18).targetTss()).isEqualTo(218.0);
            assertThat(semanas.get(19).phase()).isEqualTo(RACE_WEEK);
            assertThat(semanas.get(19).targetTss()).isEqualTo(164.0);
        }

        @Test
        @DisplayName("volume segue o fator do TSS da semana sobre o TSS de partida")
        void volumeSegueFatorDoTss() {
            SeasonPlan plano = plan(DistanciaProva.KM_42, null, LocalDate.of(2027, 4, 4), 40.0);

            // 40 km × 364/280 = 52,0; 40 × 164/280 = 23,4
            assertThat(plano.weeks().get(16).targetVolumeKm()).isEqualByComparingTo("52.0");
            assertThat(plano.weeks().get(19).targetVolumeKm()).isEqualByComparingTo("23.4");
        }

        @Test
        @DisplayName("fases saem na ordem Base → Construção → Pico → Polimento → Semana da prova")
        void ordemDasFases() {
            SeasonPlan plano = plan(DistanciaProva.KM_21, null, HOJE.plusWeeks(11).plusDays(6), 40.0);

            assertThat(plano.weeks()).extracting(WeekPlan::phase).containsExactly(
                    BASE, BASE, BASE, BASE, BUILD, BUILD, BUILD, PEAK, PEAK, PEAK, TAPER, RACE_WEEK);
        }

        @Test
        @DisplayName("KM_21 a 6 semanas com CTL ≥ 40 corta Base primeiro")
        void curtaComBaseCortaBase() {
            SeasonPlan plano = plan(DistanciaProva.KM_21, null, HOJE.plusWeeks(5).plusDays(6), 40.0);

            assertThat(plano.shortPreparation()).isTrue();
            assertThat(contagem(plano)).containsExactlyInAnyOrderEntriesOf(
                    Map.of(BUILD, 1L, PEAK, 3L, TAPER, 1L, RACE_WEEK, 1L));
        }

        @Test
        @DisplayName("KM_21 a 6 semanas com CTL < 40 preserva a Base")
        void curtaSemBasePreservaBase() {
            SeasonPlan plano = plan(DistanciaProva.KM_21, null, HOJE.plusWeeks(5).plusDays(6), 39.9);

            assertThat(contagem(plano)).containsExactlyInAnyOrderEntriesOf(
                    Map.of(BASE, 4L, TAPER, 1L, RACE_WEEK, 1L));
        }

        @Test
        @DisplayName("KM_42 a 3 semanas sem semana de subida: o polimento parte do TSS de partida")
        void semSubidaUsaPartida() {
            SeasonPlan plano = plan(DistanciaProva.KM_42, null, HOJE.plusWeeks(2).plusDays(6), 40.0);

            assertThat(plano.weeks()).extracting(WeekPlan::phase).containsExactly(TAPER, TAPER, RACE_WEEK);
            assertThat(plano.weeks()).extracting(WeekPlan::targetTss).containsExactly(168.0, 168.0, 126.0);
        }

        @Test
        @DisplayName("KM_42 a 2 semanas corta também o Polimento, nunca a Semana da prova")
        void cortaPolimentoPorUltimo() {
            SeasonPlan plano = plan(DistanciaProva.KM_42, null, HOJE.plusWeeks(1).plusDays(6), 40.0);

            assertThat(plano.weeks()).extracting(WeekPlan::phase).containsExactly(TAPER, RACE_WEEK);
        }

        @Test
        @DisplayName("subida semanal limitada a +8 CTL (56 TSS) mesmo quando o pico pede mais")
        void rampaLimitada() {
            // KM_5 a 2 semanas: Pico 1 + Semana da prova; S = 1000 pediria +300 numa semana
            SeasonPlan plano = SeasonPlanner.plan(
                    DistanciaProva.KM_5, null, HOJE.plusWeeks(1).plusDays(6), HOJE, 1000.0 / 7, VOLUME);

            assertThat(plano.weeks().get(0).phase()).isEqualTo(PEAK);
            assertThat(plano.weeks().get(0).targetTss()).isEqualTo(1056.0);
        }

        @Test
        @DisplayName("CUSTOMIZADA usa a linha da faixa de RacePreparationRule")
        void customizadaUsaFaixa() {
            SeasonPlan plano = plan(DistanciaProva.CUSTOMIZADA, new BigDecimal("25"),
                    HOJE.plusWeeks(11).plusDays(6), 40.0);

            assertThat(contagem(plano)).containsExactlyInAnyOrderEntriesOf(
                    Map.of(BASE, 4L, BUILD, 3L, PEAK, 3L, TAPER, 1L, RACE_WEEK, 1L));
            assertThat(plano.shortPreparation()).isFalse();
        }

        @Test
        @DisplayName("mesma entrada gera semanas idênticas")
        void reprodutivel() {
            SeasonPlan a = plan(DistanciaProva.KM_42, null, LocalDate.of(2027, 4, 4), 37.3);
            SeasonPlan b = plan(DistanciaProva.KM_42, null, LocalDate.of(2027, 4, 4), 37.3);

            assertThat(a).isEqualTo(b);
        }

        @Test
        @DisplayName("recusa horizonte menor que 2 semanas e bases não positivas")
        void recusaEntradaInvalida() {
            assertThatThrownBy(() -> plan(DistanciaProva.KM_10, null, HOJE.plusDays(6), 40.0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> plan(DistanciaProva.KM_10, null, HOJE.plusWeeks(10), 0.0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> SeasonPlanner.plan(DistanciaProva.KM_10, null, HOJE.plusWeeks(10), HOJE,
                    40.0, BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    private static SeasonPlan plan(DistanciaProva distancia, BigDecimal km, LocalDate prova, double ctl) {
        return SeasonPlanner.plan(distancia, km, prova, HOJE, ctl, VOLUME);
    }

    private static Map<TrainingPhase, Long> contagem(SeasonPlan plano) {
        return plano.weeks().stream()
                .collect(Collectors.groupingBy(WeekPlan::phase, Collectors.counting()));
    }
}
