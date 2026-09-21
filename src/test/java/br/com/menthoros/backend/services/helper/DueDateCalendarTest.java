package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.enums.ContractPeriodicity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DueDateCalendarTest {

    @Nested
    @DisplayName("firstDueDate")
    class FirstDueDate {

        @ParameterizedTest(name = "início {0}, dia {1} → {2}")
        @DisplayName("primeiro dia do contrato maior ou igual ao início, com clamp")
        @CsvSource({
                "2026-09-09, 10, 2026-09-10",   // ainda não passou: mesmo mês
                "2026-09-10, 10, 2026-09-10",   // exatamente no dia: mesmo mês
                "2026-09-11, 10, 2026-10-10",   // já passou: mês seguinte
                "2026-09-21, 10, 2026-10-10",   // CA2
                "2026-02-01, 31, 2026-02-28",   // clamp em fevereiro
                "2024-02-01, 31, 2024-02-29",   // bissexto
                "2026-02-28, 31, 2026-02-28",   // clamp cai exatamente no início
                "2026-03-01, 31, 2026-03-31",
                "2026-12-31, 1,  2027-01-01",   // vira o ano
        })
        void primeiroVencimento(LocalDate start, int dueDay, LocalDate expected) {
            assertThat(DueDateCalendar.firstDueDate(start, dueDay)).isEqualTo(expected);
        }

        @ParameterizedTest
        @ValueSource(ints = {0, 32, -1})
        @DisplayName("rejeita dia fora de 1–31")
        void rejeitaDiaInvalido(int dueDay) {
            assertThatThrownBy(() -> DueDateCalendar.firstDueDate(LocalDate.of(2026, 1, 1), dueDay))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("dueDay");
        }

        @Test
        @DisplayName("rejeita início nulo")
        void rejeitaInicioNulo() {
            assertThatThrownBy(() -> DueDateCalendar.firstDueDate(null, 10))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("nextDueDate")
    class NextDueDate {

        @ParameterizedTest(name = "{0} + {1} dia {2} → {3}")
        @DisplayName("anterior + N meses, no dia do contrato com clamp")
        @CsvSource({
                "2027-01-31, MONTHLY,    31, 2027-02-28",   // spec: dia 31 em fevereiro
                "2027-02-28, MONTHLY,    31, 2027-03-31",   // volta ao 31 — clamp pelo dia do contrato
                "2024-01-31, MONTHLY,    31, 2024-02-29",   // bissexto
                "2026-01-30, MONTHLY,    30, 2026-02-28",   // dia 30 em fevereiro
                "2026-10-10, MONTHLY,    10, 2026-11-10",
                "2026-10-10, QUARTERLY,  10, 2027-01-10",
                "2026-10-10, SEMIANNUAL, 10, 2027-04-10",
                "2026-10-10, ANNUAL,     10, 2027-10-10",
                "2026-11-30, QUARTERLY,  31, 2027-02-28",   // trimestral caindo em fevereiro
                "2026-12-10, MONTHLY,    10, 2027-01-10",   // vira o ano
        })
        void proximoVencimento(LocalDate previous, ContractPeriodicity periodicity, int dueDay, LocalDate expected) {
            assertThat(DueDateCalendar.nextDueDate(previous, periodicity, dueDay)).isEqualTo(expected);
        }

        @Test
        @DisplayName("rejeita anterior ou periodicidade nulos")
        void rejeitaNulos() {
            assertThatThrownBy(() -> DueDateCalendar.nextDueDate(null, ContractPeriodicity.MONTHLY, 10))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> DueDateCalendar.nextDueDate(LocalDate.of(2026, 1, 1), null, 10))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("rejeita dia fora de 1–31")
        void rejeitaDiaInvalido() {
            assertThatThrownBy(() -> DueDateCalendar.nextDueDate(LocalDate.of(2026, 1, 1), ContractPeriodicity.MONTHLY, 32))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
