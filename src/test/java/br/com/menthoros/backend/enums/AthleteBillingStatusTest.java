package br.com.menthoros.backend.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AthleteBillingStatusTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 9, 21);

    @Nested
    @DisplayName("resolve")
    class Resolve {

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("sem mensalidade em aberto → UP_TO_DATE (quem decide presença é o serviço)")
        void semAbertas(List<LocalDate> abertas) {
            assertThat(AthleteBillingStatus.resolve(abertas, HOJE)).isEqualTo(AthleteBillingStatus.UP_TO_DATE);
        }

        @Test
        @DisplayName("uma vencida ontem → OVERDUE")
        void vencidaOntem() {
            assertThat(AthleteBillingStatus.resolve(List.of(HOJE.minusDays(1)), HOJE)).isEqualTo(AthleteBillingStatus.OVERDUE);
        }

        @Test
        @DisplayName("vence hoje → DUE_SOON (não é vencida)")
        void venceHoje() {
            assertThat(AthleteBillingStatus.resolve(List.of(HOJE), HOJE)).isEqualTo(AthleteBillingStatus.DUE_SOON);
        }

        @Test
        @DisplayName("vence em exatamente 7 dias → DUE_SOON; em 8 → UP_TO_DATE (janela fechada)")
        void bordaDaJanela() {
            assertThat(AthleteBillingStatus.resolve(List.of(HOJE.plusDays(7)), HOJE)).isEqualTo(AthleteBillingStatus.DUE_SOON);
            assertThat(AthleteBillingStatus.resolve(List.of(HOJE.plusDays(8)), HOJE)).isEqualTo(AthleteBillingStatus.UP_TO_DATE);
        }

        @Test
        @DisplayName("mistas: qualquer vencida domina, mesmo com outra em dia")
        void mistasVencidaDomina() {
            assertThat(AthleteBillingStatus.resolve(List.of(HOJE.plusDays(30), HOJE.minusDays(2)), HOJE))
                    .isEqualTo(AthleteBillingStatus.OVERDUE);
        }

        @Test
        @DisplayName("mistas sem vencida: a mais próxima decide")
        void mistasMaisProximaDecide() {
            assertThat(AthleteBillingStatus.resolve(List.of(HOJE.plusDays(30), HOJE.plusDays(3)), HOJE))
                    .isEqualTo(AthleteBillingStatus.DUE_SOON);
        }

        @Test
        @DisplayName("elemento nulo na lista é ignorado")
        void ignoraNulo() {
            assertThat(AthleteBillingStatus.resolve(Arrays.asList(null, HOJE.plusDays(30)), HOJE))
                    .isEqualTo(AthleteBillingStatus.UP_TO_DATE);
        }

        @Test
        @DisplayName("hoje nulo é erro de programação")
        void hojeNulo() {
            assertThatThrownBy(() -> AthleteBillingStatus.resolve(List.of(HOJE), null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
