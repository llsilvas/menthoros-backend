package br.com.menthoros.backend.domain.planner;

import br.com.menthoros.backend.domain.planner.SeasonPlanner.RaceMilestone;
import br.com.menthoros.backend.domain.planner.SeasonPlanner.SeasonPlan;
import br.com.menthoros.backend.domain.planner.SeasonPlanner.WeekPlan;
import br.com.menthoros.backend.enums.DistanciaProva;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
    /** Domingo: 16 semanas exatas, o template de maratona sem semanas extras. */
    private static final LocalDate PROVA_16_SEMANAS = HOJE.plusWeeks(15).plusDays(6);

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
            assertThat(semanas.get(19).phase()).isEqualTo(RACE_WEEK);
        }

        @Test
        @DisplayName("volume segue o fator do TSS da semana sobre o TSS de partida")
        void volumeSegueFatorDoTss() {
            SeasonPlan plano = plan(DistanciaProva.KM_42, null, LocalDate.of(2027, 4, 4), 40.0);

            // 40 km × 364/280 = 52,0; 40 × 162/280 = 23,1
            assertThat(plano.weeks().get(16).targetVolumeKm()).isEqualByComparingTo("52.0");
            assertThat(plano.weeks().get(19).targetVolumeKm()).isEqualByComparingTo("23.1");
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
            // 280 × (1 − redução da TaperStrategy a 20, 13 e 6 dias)
            assertThat(plano.weeks()).extracting(WeekPlan::targetTss).containsExactly(172.0, 151.0, 125.0);
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
        @DisplayName("descarga 3:1 na 4ª e 8ª semana de Base+Construção, 20% abaixo da rampa, e a seguinte volta à rampa")
        void descargaTresPorUm() {
            SeasonPlan plano = plan(DistanciaProva.KM_42, null, PROVA_16_SEMANAS, 40.0);
            List<WeekPlan> s = plano.weeks();

            assertThat(s).filteredOn(WeekPlan::deload).extracting(WeekPlan::weekIndex).containsExactly(4, 8);
            // rampa 280 → 364 em 13 semanas: semana 4 = 305,85 × 0,80; semana 5 = 312,31
            assertThat(s.get(3).targetTss()).isEqualTo(245.0);
            assertThat(s.get(3).targetVolumeKm()).isEqualByComparingTo("35.0");
            assertThat(s.get(4).targetTss()).isEqualTo(312.0);
            assertThat(s.get(7).targetTss()).isEqualTo(265.0);
            assertThat(s.get(8).targetTss()).isEqualTo(338.0);
        }

        @Test
        @DisplayName("nunca há descarga em Pico, Polimento ou Semana da prova")
        void descargaSoEmBaseEConstrucao() {
            SeasonPlan plano = plan(DistanciaProva.KM_42, null, LocalDate.of(2027, 4, 4), 40.0);

            assertThat(plano.weeks()).filteredOn(WeekPlan::deload)
                    .allSatisfy(w -> assertThat(w.phase()).isIn(BASE, BUILD))
                    .extracting(WeekPlan::weekIndex).containsExactly(4, 8, 12);
        }

        @Test
        @DisplayName("longão = 28% do volume, 25% menor na descarga e ausente na Semana da prova")
        void longao() {
            List<WeekPlan> s = plan(DistanciaProva.KM_42, null, PROVA_16_SEMANAS, 40.0).weeks();

            assertThat(s.get(0).longRunKm()).isEqualByComparingTo("11.5");   // 40,9 × 0,28
            assertThat(s.get(3).longRunKm()).isEqualByComparingTo("7.4");    // 35,0 × 0,28 × 0,75
            assertThat(s.get(4).longRunKm()).isEqualByComparingTo("12.5");   // 44,6 × 0,28
            assertThat(s.get(15).phase()).isEqualTo(RACE_WEEK);
            assertThat(s.get(15).longRunKm()).isNull();
        }

        @Test
        @DisplayName("com longão de partida, o longão segue a proporção do volume e cai 25% na descarga")
        void longaoProporcionalAoDePartida() {
            List<WeekPlan> s = comLongao(DistanciaProva.KM_42, "14").weeks();

            assertThat(s.get(0).longRunKm()).isEqualByComparingTo("14.3");   // 14 × 40,9 / 40
            assertThat(s.get(3).longRunKm()).isEqualByComparingTo("9.2");    // 14 × 35,0 / 40 × 0,75
            assertThat(s.get(4).longRunKm()).isEqualByComparingTo("15.6");   // 14 × 44,6 / 40
            assertThat(s.get(15).longRunKm()).isNull();
        }

        @Test
        @DisplayName("longão de partida curto não derruba o piso de 28% do volume")
        void longaoDePartidaAbaixoDoPiso() {
            List<WeekPlan> s = comLongao(DistanciaProva.KM_42, "5").weeks();

            assertThat(s.get(0).longRunKm()).isEqualByComparingTo("11.5");   // 40,9 × 0,28
        }

        @Test
        @DisplayName("longão de partida longo fica limitado a 50% do volume da semana")
        void longaoDePartidaAcimaDaMetade() {
            List<WeekPlan> s = comLongao(DistanciaProva.KM_42, "25").weeks();

            assertThat(s.get(0).longRunKm()).isEqualByComparingTo("20.5");   // 40,9 × 0,50
        }

        @Test
        @DisplayName("longão de partida ainda respeita o teto da distância")
        void longaoDePartidaNoTetoDaDistancia() {
            List<WeekPlan> s = comLongao(DistanciaProva.KM_10, "20").weeks();

            assertThat(s.get(0).longRunKm()).isEqualByComparingTo("16");
        }

        @Test
        @DisplayName("sem longão de partida, mesmas semanas do cálculo de 28%")
        void semLongaoDePartida() {
            assertThat(SeasonPlanner.plan(DistanciaProva.KM_42, null, PROVA_16_SEMANAS, HOJE, 40.0, VOLUME, null,
                    List.of()).weeks())
                    .isEqualTo(plan(DistanciaProva.KM_42, null, PROVA_16_SEMANAS, 40.0).weeks());
        }

        @ParameterizedTest(name = "{0} {1} km → teto {2} km")
        @CsvSource({
                "KM_5, , 12", "KM_10, , 16", "KM_21, , 21", "KM_42, , 32",
                "CUSTOMIZADA, 5.5, 12", "CUSTOMIZADA, 5.6, 16", "CUSTOMIZADA, 10.5, 16",
                "CUSTOMIZADA, 21.5, 21", "CUSTOMIZADA, 21.6, 32"
        })
        @DisplayName("longão limitado pelo teto da distância")
        void tetoDoLongao(DistanciaProva distancia, BigDecimal km, String teto) {
            SeasonPlan plano = SeasonPlanner.plan(distancia, km, HOJE.plusWeeks(15).plusDays(6), HOJE,
                    40.0, new BigDecimal("200"));

            assertThat(plano.weeks().get(0).longRunKm()).isEqualByComparingTo(teto);
        }

        @Test
        @DisplayName("taper pela TaperStrategy na segunda-feira de cada semana, sobre o pico fixo, uma vez")
        void taperPelaCurva() {
            List<WeekPlan> s = plan(DistanciaProva.KM_42, null, LocalDate.of(2027, 4, 4), 40.0).weeks();

            // pico 364; reduções de 38,4%, 46,2% e 55,5% a 20, 13 e 6 dias da prova de domingo
            assertThat(s.subList(17, 20)).extracting(WeekPlan::phase).containsExactly(TAPER, TAPER, RACE_WEEK);
            assertThat(s.subList(17, 20)).extracting(WeekPlan::targetTss).containsExactly(224.0, 196.0, 162.0);
        }

        @Test
        @DisplayName("prova no sábado conta os dias da segunda até o sábado")
        void taperProvaNoSabado() {
            List<WeekPlan> s = plan(DistanciaProva.KM_42, null, LocalDate.of(2027, 4, 3), 40.0).weeks();

            assertThat(s.subList(17, 20)).extracting(WeekPlan::targetTss).containsExactly(220.0, 191.0, 157.0);
        }

        @Test
        @DisplayName("template sem Polimento ainda reduz a Semana da prova pela curva")
        void semanaDaProvaSemPolimento() {
            List<WeekPlan> s = plan(DistanciaProva.KM_10, null, HOJE.plusWeeks(7).plusDays(6), 40.0).weeks();

            assertThat(s).extracting(WeekPlan::phase).doesNotContain(TAPER);
            assertThat(s.get(7).phase()).isEqualTo(RACE_WEEK);
            assertThat(s.get(7).targetTss()).isEqualTo(162.0);
        }

        @Test
        @DisplayName("prova preparatória vira marco da semana que contém a data; duas na mesma semana, fica a mais longa")
        void marcoNaSemanaDaPreparatoria() {
            UUID dezK = UUID.randomUUID();
            UUID meia = UUID.randomUUID();
            UUID cincoK = UUID.randomUUID();
            List<RaceMilestone> candidatas = List.of(
                    new RaceMilestone(dezK, HOJE.plusWeeks(5).plusDays(2), new BigDecimal("10")),
                    new RaceMilestone(meia, HOJE.plusWeeks(5).plusDays(6), new BigDecimal("21.1")),
                    new RaceMilestone(cincoK, HOJE.plusWeeks(9), new BigDecimal("5")));

            List<WeekPlan> s = plan(DistanciaProva.KM_42, PROVA_16_SEMANAS, candidatas).weeks();

            assertThat(s.get(5).milestoneRaceId()).isEqualTo(meia);
            assertThat(s.get(9).milestoneRaceId()).isEqualTo(cincoK);
            assertThat(s).filteredOn(w -> w.milestoneRaceId() != null).hasSize(2);
        }

        @Test
        @DisplayName("mesma distância na mesma semana: fica a mais cedo")
        void empateFicaAMaisCedo() {
            UUID sabado = UUID.randomUUID();
            UUID terca = UUID.randomUUID();
            List<RaceMilestone> candidatas = List.of(
                    new RaceMilestone(sabado, HOJE.plusWeeks(5).plusDays(5), new BigDecimal("10")),
                    new RaceMilestone(terca, HOJE.plusWeeks(5).plusDays(1), new BigDecimal("10")));

            List<WeekPlan> s = plan(DistanciaProva.KM_42, PROVA_16_SEMANAS, candidatas).weeks();

            assertThat(s.get(5).milestoneRaceId()).isEqualTo(terca);
        }

        @Test
        @DisplayName("preparatória a menos de 7 dias da alvo não vira marco")
        void preparatoriaColadaNaAlvoFicaDeFora() {
            List<RaceMilestone> candidatas = List.of(
                    new RaceMilestone(UUID.randomUUID(), PROVA_16_SEMANAS.minusDays(6), new BigDecimal("10")));

            List<WeekPlan> s = plan(DistanciaProva.KM_42, PROVA_16_SEMANAS, candidatas).weeks();

            assertThat(s).allSatisfy(w -> assertThat(w.milestoneRaceId()).isNull());
        }

        @Test
        @DisplayName("descarga que cai na semana do marco passa para a seguinte de Base/Construção")
        void descargaDesviaDoMarco() {
            List<RaceMilestone> candidatas = List.of(
                    new RaceMilestone(UUID.randomUUID(), HOJE.plusWeeks(3).plusDays(6), new BigDecimal("10")));

            List<WeekPlan> s = plan(DistanciaProva.KM_42, PROVA_16_SEMANAS, candidatas).weeks();

            assertThat(s).filteredOn(WeekPlan::deload).extracting(WeekPlan::weekIndex).containsExactly(5, 8);
            assertThat(s.get(3).targetTss()).isEqualTo(306.0);
        }

        @Test
        @DisplayName("descarga na última semana de Construção com marco é pulada")
        void descargaPuladaSemSemanaSeguinte() {
            // KM_21 a 13 semanas: Base 5 + Construção 3 → descargas nas semanas 4 e 8; a 8 é a última de subida
            LocalDate prova = HOJE.plusWeeks(12).plusDays(6);
            List<RaceMilestone> candidatas = List.of(
                    new RaceMilestone(UUID.randomUUID(), HOJE.plusWeeks(7).plusDays(6), new BigDecimal("10")));

            List<WeekPlan> s = plan(DistanciaProva.KM_21, prova, candidatas).weeks();

            assertThat(s.get(7).phase()).isEqualTo(BUILD);
            assertThat(s.get(8).phase()).isEqualTo(PEAK);
            assertThat(s).filteredOn(WeekPlan::deload).extracting(WeekPlan::weekIndex).containsExactly(4);
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

    @Nested
    @DisplayName("planWithPhases")
    class PlanWithPhases {

        @Test
        @DisplayName("respeita as semanas por fase pedidas, na ordem Base → Construção → Pico → Polimento → Prova")
        void respeitaFases() {
            SeasonPlan plano = SeasonPlanner.planWithPhases(DistanciaProva.KM_42, null, PROVA_16_SEMANAS, HOJE,
                    40.0, VOLUME, null, List.of(), Map.of(BASE, 3, BUILD, 6, PEAK, 4, TAPER, 2));

            assertThat(plano.weeks()).extracting(WeekPlan::phase).containsExactly(
                    BASE, BASE, BASE, BUILD, BUILD, BUILD, BUILD, BUILD, BUILD,
                    PEAK, PEAK, PEAK, PEAK, TAPER, TAPER, RACE_WEEK);
            assertThat(plano.weeks().get(0).weekStart()).isEqualTo(HOJE);
            assertThat(plano.weeks().get(15).weekStart()).isEqualTo(PROVA_16_SEMANAS.minusDays(6));
        }

        @Test
        @DisplayName("com as fases do template gera as mesmas semanas que plan")
        void mesmasSemanasDoTemplate() {
            SeasonPlan template = plan(DistanciaProva.KM_42, null, PROVA_16_SEMANAS, 40.0);

            SeasonPlan explicito = SeasonPlanner.planWithPhases(DistanciaProva.KM_42, null, PROVA_16_SEMANAS, HOJE,
                    40.0, VOLUME, null, List.of(), Map.of(BASE, 6, BUILD, 4, PEAK, 3, TAPER, 2));

            assertThat(explicito).isEqualTo(template);
        }

        @Test
        @DisplayName("fase com zero semanas some da sequência")
        void faseZerada() {
            SeasonPlan plano = SeasonPlanner.planWithPhases(DistanciaProva.KM_42, null, PROVA_16_SEMANAS, HOJE,
                    40.0, VOLUME, null, List.of(), Map.of(BASE, 9, BUILD, 4, PEAK, 2, TAPER, 0));

            assertThat(contagem(plano)).containsExactlyInAnyOrderEntriesOf(
                    Map.of(BASE, 9L, BUILD, 4L, PEAK, 2L, RACE_WEEK, 1L));
        }

        @Test
        @DisplayName("soma das fases + semana da prova diferente do total, fase negativa ou ausente: IllegalArgumentException")
        void somaInvalida() {
            assertThatThrownBy(() -> SeasonPlanner.planWithPhases(DistanciaProva.KM_42, null, PROVA_16_SEMANAS, HOJE,
                    40.0, VOLUME, null, List.of(), Map.of(BASE, 6, BUILD, 4, PEAK, 3, TAPER, 1)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> SeasonPlanner.planWithPhases(DistanciaProva.KM_42, null, PROVA_16_SEMANAS, HOJE,
                    40.0, VOLUME, null, List.of(), Map.of(BASE, 8, BUILD, 4, PEAK, 3, TAPER, -1)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> SeasonPlanner.planWithPhases(DistanciaProva.KM_42, null, PROVA_16_SEMANAS, HOJE,
                    40.0, VOLUME, null, List.of(), Map.of(BASE, 8, BUILD, 4, PEAK, 3)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("reanchor")
    class Reanchor {

        /** Caso real do proposal: 15 semanas, subida até a 13, descargas na 4 e na 8, Polimento na 14. */
        private static final LocalDate PROVA = HOJE.plusWeeks(14).plusDays(6);
        private static final Set<Integer> SEGUINTES = IntStream.rangeClosed(3, 15).boxed().collect(Collectors.toSet());

        @Test
        @DisplayName("a rampa parte da âncora e chega ao pico na última semana de subida")
        void rampaDaAncoraAtePico() {
            List<WeekPlan> s = reancorar(semanas("8.0"), 2, "30", SEGUINTES);

            assertThat(s.get(2).targetTss()).isEqualTo(197.0);
            assertThat(s.get(2).targetVolumeKm()).isEqualByComparingTo("21.0");
            assertThat(s.get(3).targetTss()).isEqualTo(164.0);
            assertThat(s.get(3).targetVolumeKm()).isEqualByComparingTo("17.4");
            assertThat(s.get(12).targetTss()).isEqualTo(282.0);
            assertThat(s.get(12).targetVolumeKm()).isEqualByComparingTo("30.0");
        }

        @Test
        @DisplayName("semanas anteriores, a âncora e as fora da lista de livres não mudam")
        void preservaNaoLivres() {
            List<WeekPlan> antes = semanas("8.0");
            Set<Integer> livres = new HashSet<>(SEGUINTES);
            livres.remove(6);

            List<WeekPlan> s = reancorar(antes, 2, "30", livres);

            assertThat(s).hasSize(15);
            assertThat(s.get(0)).isEqualTo(antes.get(0));
            assertThat(s.get(1)).isEqualTo(antes.get(1));
            assertThat(s.get(5)).isEqualTo(antes.get(5));
        }

        @Test
        @DisplayName("fase, descarga, data e marco das seguintes ficam como estavam")
        void preservaEstrutura() {
            List<WeekPlan> antes = semanas("8.0");

            List<WeekPlan> s = reancorar(antes, 2, "30", SEGUINTES);

            assertThat(s).extracting(WeekPlan::phase).containsExactlyElementsOf(antes.stream().map(WeekPlan::phase).toList());
            assertThat(s).extracting(WeekPlan::deload).containsExactlyElementsOf(antes.stream().map(WeekPlan::deload).toList());
            assertThat(s).extracting(WeekPlan::weekStart).containsExactlyElementsOf(antes.stream().map(WeekPlan::weekStart).toList());
        }

        @Test
        @DisplayName("Polimento e semana da prova partem do TSS calculado da última semana de subida")
        void polimentoSobreOPico() {
            List<WeekPlan> s = reancorar(semanas("8.0"), 2, "30", SEGUINTES);

            assertThat(s.get(13).targetTss()).isEqualTo(polimento(282, s.get(13).weekStart()));
            assertThat(s.get(14).targetTss()).isEqualTo(polimento(282, s.get(14).weekStart()));
        }

        @Test
        @DisplayName("última semana de subida preservada: Polimento parte do TSS gravado nela")
        void polimentoSobreSemanaPreservada() {
            List<WeekPlan> antes = new ArrayList<>(semanas("8.0"));
            antes.set(12, semana(13, PEAK, "20.0", 188, false, null));
            Set<Integer> livres = new HashSet<>(SEGUINTES);
            livres.remove(13);

            List<WeekPlan> s = reancorar(antes, 2, "30", livres);

            assertThat(s.get(12).targetTss()).isEqualTo(188.0);
            assertThat(s.get(13).targetTss()).isEqualTo(polimento(188, s.get(13).weekStart()));
        }

        @Test
        @DisplayName("última semana de subida preservada com outra razão TSS/km: volume do Polimento segue a dela")
        void polimentoNaRazaoDaSemanaPreservada() {
            List<WeekPlan> antes = new ArrayList<>(semanas("8.0"));
            antes.set(12, semana(13, PEAK, "30.0", 564, false, null));
            Set<Integer> livres = new HashSet<>(SEGUINTES);
            livres.remove(13);

            List<WeekPlan> s = reancorar(antes, 2, "30", livres);

            double tss = polimento(564, s.get(13).weekStart());
            assertThat(s.get(13).targetTss()).isEqualTo(tss);
            assertThat(s.get(13).targetVolumeKm())
                    .isEqualByComparingTo(BigDecimal.valueOf(30 * tss / 564).setScale(1, RoundingMode.HALF_UP))
                    .isLessThan(new BigDecimal("30.0"));
        }

        @Test
        @DisplayName("última semana de subida livre e em descarga: Polimento parte do TSS antes do fator")
        void polimentoAntesDaDescarga() {
            List<WeekPlan> antes = new ArrayList<>(semanas("8.0"));
            antes.set(12, semana(13, PEAK, "13.0", 122, true, null));

            List<WeekPlan> s = reancorar(antes, 2, "30", SEGUINTES);

            assertThat(s.get(12).targetTss()).isEqualTo(226.0);
            assertThat(s.get(13).targetTss()).isEqualTo(polimento(282, s.get(13).weekStart()));
        }

        @Test
        @DisplayName("longão segue a proporção da âncora: 8,0 em 20 km dá 12,0 em 30 km")
        void longaoPelaAncora() {
            List<WeekPlan> s = reancorar(semanas("8.0"), 2, "30", SEGUINTES);

            assertThat(s.get(12).longRunKm()).isEqualByComparingTo("12.0");
            assertThat(s.get(14).longRunKm()).isNull();
        }

        @Test
        @DisplayName("âncora sem longão: longão de 28% do volume")
        void longaoSemAncora() {
            List<WeekPlan> s = reancorar(semanas(null), 2, "30", SEGUINTES);

            assertThat(s.get(12).longRunKm()).isEqualByComparingTo("8.4");
        }

        @Test
        @DisplayName("pico acima do teto de rampa: a curva para no teto")
        void picoAcimaDoTeto() {
            List<WeekPlan> s = reancorar(semanas("8.0"), 2, "100", SEGUINTES);

            // 188 + 56 × 11 = 804, abaixo dos 940 pedidos
            assertThat(s.get(12).targetTss()).isEqualTo(804.0);
            assertThat(s.get(12).targetVolumeKm()).isEqualByComparingTo("85.5");
        }

        @Test
        @DisplayName("âncora na última semana de subida: só o Polimento muda, a partir do TSS da âncora")
        void semSubidaDepoisDaAncora() {
            List<WeekPlan> antes = new ArrayList<>(semanas("8.0"));
            antes.set(12, semana(13, PEAK, "20.0", 188, false, "8.0"));

            List<WeekPlan> s = reancorar(antes, 13, "30", Set.of(14, 15));

            assertThat(s.get(12)).isEqualTo(antes.get(12));
            assertThat(s.get(13).targetTss()).isEqualTo(polimento(188, s.get(13).weekStart()));
        }

        @Test
        @DisplayName("âncora com TSS zero é recusada")
        void ancoraZerada() {
            List<WeekPlan> antes = new ArrayList<>(semanas("8.0"));
            antes.set(1, semana(2, BASE, "20.0", 0, false, null));

            assertThatThrownBy(() -> reancorar(antes, 2, "30", SEGUINTES))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        private static List<WeekPlan> reancorar(List<WeekPlan> semanas, int ancora, String pico, Set<Integer> livres) {
            return SeasonPlanner.reanchor(semanas, ancora, new BigDecimal(pico), livres, PROVA, DistanciaProva.KM_21, null);
        }

        private static List<WeekPlan> semanas(String longaoAncora) {
            List<WeekPlan> s = new ArrayList<>();
            for (int i = 1; i <= 15; i++) {
                TrainingPhase fase = i <= 7 ? BASE : i <= 10 ? BUILD : i <= 13 ? PEAK : i == 14 ? TAPER : RACE_WEEK;
                s.add(i == 2
                        ? semana(i, fase, "20.0", 188, false, longaoAncora)
                        : semana(i, fase, "13.0", 122, i == 4 || i == 8, "3.6"));
            }
            return s;
        }

        private static WeekPlan semana(int indice, TrainingPhase fase, String volume, double tss, boolean descarga,
                                       String longao) {
            return new WeekPlan(indice, HOJE.plusWeeks(indice - 1L), fase, new BigDecimal(volume), tss, descarga,
                    longao == null ? null : new BigDecimal(longao), null);
        }

        private static double polimento(double referencia, LocalDate weekStart) {
            return Math.round(referencia * (1 - new TaperStrategy().resolverReducaoPercentual(
                    ChronoUnit.DAYS.between(weekStart, PROVA))));
        }
    }

    private static SeasonPlan plan(DistanciaProva distancia, BigDecimal km, LocalDate prova, double ctl) {
        return SeasonPlanner.plan(distancia, km, prova, HOJE, ctl, VOLUME);
    }

    private static SeasonPlan plan(DistanciaProva distancia, LocalDate prova, List<RaceMilestone> candidatas) {
        return SeasonPlanner.plan(distancia, null, prova, HOJE, 40.0, VOLUME, null, candidatas);
    }

    private static SeasonPlan comLongao(DistanciaProva distancia, String longaoKm) {
        return SeasonPlanner.plan(distancia, null, PROVA_16_SEMANAS, HOJE, 40.0, VOLUME, new BigDecimal(longaoKm),
                List.of());
    }

    private static Map<TrainingPhase, Long> contagem(SeasonPlan plano) {
        return plano.weeks().stream()
                .collect(Collectors.groupingBy(WeekPlan::phase, Collectors.counting()));
    }
}
