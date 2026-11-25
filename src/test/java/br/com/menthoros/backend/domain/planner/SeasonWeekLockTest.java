package br.com.menthoros.backend.domain.planner;

import br.com.menthoros.backend.domain.planner.SeasonWeekLock.Reason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class SeasonWeekLockTest {

    /** Segunda-feira da semana avaliada. */
    private static final LocalDate SEGUNDA = LocalDate.of(2026, 11, 16);
    private static final LocalDate PROVA_LONGE = LocalDate.of(2027, 4, 4);

    @Nested
    @DisplayName("of")
    class Of {

        @ParameterizedTest(name = "hoje {0} → {1}")
        @CsvSource({
                "2026-11-15, ",              // domingo anterior: futura, livre
                "2026-11-16, IN_PROGRESS",   // a própria segunda
                "2026-11-22, IN_PROGRESS",   // domingo da semana
                "2026-11-23, COMPLETED",     // segunda seguinte
                "2027-01-10, COMPLETED"
        })
        @DisplayName("concluída e em andamento pela data de hoje")
        void pelaDataDeHoje(LocalDate hoje, Reason esperado) {
            assertThat(SeasonWeekLock.of(SEGUNDA, hoje, PROVA_LONGE).orElse(null)).isEqualTo(esperado);
        }

        @Test
        @DisplayName("semana futura que contém a prova-alvo (até o domingo) é a da prova")
        void semanaDaProva() {
            LocalDate hoje = SEGUNDA.minusWeeks(3);

            assertThat(SeasonWeekLock.of(SEGUNDA, hoje, SEGUNDA.plusDays(6))).contains(Reason.RACE_WEEK);
            assertThat(SeasonWeekLock.of(SEGUNDA, hoje, SEGUNDA)).contains(Reason.RACE_WEEK);
            assertThat(SeasonWeekLock.of(SEGUNDA, hoje, SEGUNDA.plusDays(7))).isEmpty();
        }

        @Test
        @DisplayName("semana da prova já em andamento conta como em andamento")
        void provaNaSemanaCorrente() {
            assertThat(SeasonWeekLock.of(SEGUNDA, SEGUNDA.plusDays(2), SEGUNDA.plusDays(6))).contains(Reason.IN_PROGRESS);
        }

        @Test
        @DisplayName("sem data de prova, semana futura fica livre")
        void semProva() {
            assertThat(SeasonWeekLock.of(SEGUNDA, SEGUNDA.minusDays(1), null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("forPlan")
    class ForPlan {

        @Test
        @DisplayName("proposta v1: semana em andamento e semana da prova ficam livres; concluída trava")
        void primeiraProposta() {
            assertThat(SeasonWeekLock.forPlan(true, SEGUNDA, SEGUNDA.plusDays(2), PROVA_LONGE)).isEmpty();
            assertThat(SeasonWeekLock.forPlan(true, SEGUNDA, SEGUNDA.minusWeeks(3), SEGUNDA.plusDays(6))).isEmpty();
            assertThat(SeasonWeekLock.forPlan(true, SEGUNDA, SEGUNDA.plusWeeks(1), PROVA_LONGE)).contains(Reason.COMPLETED);
        }

        @ParameterizedTest(name = "hoje {0} → {1}")
        @CsvSource({"2026-11-15, ", "2026-11-18, IN_PROGRESS", "2026-11-23, COMPLETED"})
        @DisplayName("fora da proposta v1, a mesma regra de of")
        void demaisTemporadas(LocalDate hoje, Reason esperado) {
            assertThat(SeasonWeekLock.forPlan(false, SEGUNDA, hoje, PROVA_LONGE).orElse(null)).isEqualTo(esperado);
        }

        @Test
        @DisplayName("fora da proposta v1, semana da prova continua travada")
        void semanaDaProvaFora() {
            assertThat(SeasonWeekLock.forPlan(false, SEGUNDA, SEGUNDA.minusWeeks(3), SEGUNDA.plusDays(6)))
                    .contains(Reason.RACE_WEEK);
        }
    }
}
