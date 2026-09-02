package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.enums.DistanciaProva;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ProvaDerivadosCalculator")
class ProvaDerivadosCalculatorTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 9, 2);

    private final ProvaEnricher enricher = new ProvaEnricher();
    private final ProvaDerivadosCalculator calculator = new ProvaDerivadosCalculator(
            Clock.fixed(Instant.parse("2026-09-02T12:00:00Z"), ZoneOffset.UTC));

    @Nested
    @DisplayName("preparacaoCurta")
    class PreparacaoCurta {

        @Test
        @DisplayName("maratona em 8 semanas é preparação curta")
        void maratonaEmOitoSemanas() {
            assertThat(calculator.preparacaoCurta(maratonaEm(8))).isTrue();
        }

        @Test
        @DisplayName("maratona em 20 semanas não é curta")
        void maratonaEmVinteSemanas() {
            assertThat(calculator.preparacaoCurta(maratonaEm(20))).isFalse();
        }

        @Test
        @DisplayName("maratona em exatamente 16 semanas não é curta")
        void maratonaNoLimite() {
            Prova prova = maratonaEm(16);

            assertThat(prova.getInicioPreparacao()).isEqualTo(HOJE);
            assertThat(calculator.preparacaoCurta(prova)).isFalse();
        }

        @Test
        @DisplayName("prova legada sem início derivado não é curta")
        void semDerivados() {
            Prova legada = Prova.builder().dataProva(HOJE.plusWeeks(3)).distancia(DistanciaProva.KM_42).build();

            assertThat(calculator.preparacaoCurta(legada)).isFalse();
        }
    }

    @Nested
    @DisplayName("semanasFaltando")
    class SemanasFaltando {

        @Test
        @DisplayName("conta semanas inteiras até a prova")
        void contaSemanas() {
            assertThat(calculator.semanasFaltando(maratonaEm(8))).isEqualTo(8);
        }

        @Test
        @DisplayName("sem data devolve zero")
        void semData() {
            assertThat(calculator.semanasFaltando(Prova.builder().build())).isZero();
        }
    }

    private Prova maratonaEm(int semanas) {
        Prova prova = Prova.builder()
                .distancia(DistanciaProva.KM_42)
                .dataProva(HOJE.plusWeeks(semanas))
                .build();
        enricher.aplicarDerivados(prova);
        return prova;
    }
}
