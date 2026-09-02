package br.com.menthoros.backend.domain.planner;

import br.com.menthoros.backend.enums.DistanciaProva;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RacePreparationRule")
class RacePreparationRuleTest {

    @Nested
    @DisplayName("minimoSemanas")
    class MinimoSemanas {

        @ParameterizedTest(name = "{0} → {1} semanas")
        @CsvSource({"KM_5, 8", "KM_10, 10", "KM_21, 12", "KM_42, 16"})
        @DisplayName("distância padrão usa a tabela e ignora distanciaKm")
        void distanciaPadraoUsaTabela(DistanciaProva distancia, int esperado) {
            assertThat(RacePreparationRule.minimoSemanas(distancia, null)).isEqualTo(esperado);
            assertThat(RacePreparationRule.minimoSemanas(distancia, new BigDecimal("99"))).isEqualTo(esperado);
        }

        @ParameterizedTest(name = "{0} km → {1} semanas")
        @CsvSource({
                "0.1, 8",
                "7.5, 8",
                "7.6, 10",
                "15, 10",
                "15.1, 12",
                "30, 12",
                "30.1, 16",
                "200, 16"
        })
        @DisplayName("distância customizada usa as faixas 7,5 / 15 / 30")
        void customizadaUsaFaixas(String km, int esperado) {
            assertThat(RacePreparationRule.minimoSemanas(DistanciaProva.CUSTOMIZADA, new BigDecimal(km)))
                    .isEqualTo(esperado);
        }

        @Test
        @DisplayName("customizada sem quilometragem positiva é inválida")
        void customizadaSemKm() {
            assertThatThrownBy(() -> RacePreparationRule.minimoSemanas(DistanciaProva.CUSTOMIZADA, null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> RacePreparationRule.minimoSemanas(DistanciaProva.CUSTOMIZADA, BigDecimal.ZERO))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("distância nula é inválida")
        void distanciaNula() {
            assertThatThrownBy(() -> RacePreparationRule.minimoSemanas(null, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("distanciaNominalKm")
    class DistanciaNominalKm {

        @ParameterizedTest(name = "{0} → {1} km")
        @CsvSource({"KM_5, 5", "KM_10, 10", "KM_21, 21.1", "KM_42, 42.2"})
        @DisplayName("devolve o valor nominal da distância padrão")
        void nominal(DistanciaProva distancia, String esperado) {
            assertThat(RacePreparationRule.distanciaNominalKm(distancia))
                    .isEqualByComparingTo(new BigDecimal(esperado));
        }

        @Test
        @DisplayName("customizada não tem valor nominal")
        void customizadaSemNominal() {
            assertThatThrownBy(() -> RacePreparationRule.distanciaNominalKm(DistanciaProva.CUSTOMIZADA))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("inicioPreparacao")
    class InicioPreparacao {

        @Test
        @DisplayName("subtrai as semanas da data da prova")
        void subtraiSemanas() {
            assertThat(RacePreparationRule.inicioPreparacao(LocalDate.of(2026, 12, 6), 16))
                    .isEqualTo(LocalDate.of(2026, 8, 16));
        }
    }

    @Nested
    @DisplayName("semanasFaltando")
    class SemanasFaltando {

        @ParameterizedTest(name = "{0} dias → {1} semanas")
        @CsvSource({"0, 0", "6, 0", "7, 1", "55, 7", "56, 8", "112, 16", "-3, 0"})
        @DisplayName("arredonda para baixo e nunca é negativo")
        void arredondaParaBaixo(int dias, int esperado) {
            LocalDate hoje = LocalDate.of(2026, 9, 2);

            assertThat(RacePreparationRule.semanasFaltando(hoje.plusDays(dias), hoje)).isEqualTo(esperado);
        }
    }

    @Nested
    @DisplayName("preparacaoCurta")
    class PreparacaoCurta {

        private final LocalDate hoje = LocalDate.of(2026, 9, 2);

        @Test
        @DisplayName("início no passado é preparação curta")
        void inicioNoPassado() {
            assertThat(RacePreparationRule.preparacaoCurta(hoje.minusDays(1), hoje)).isTrue();
        }

        @Test
        @DisplayName("início hoje ou no futuro não é curta")
        void inicioHojeOuFuturo() {
            assertThat(RacePreparationRule.preparacaoCurta(hoje, hoje)).isFalse();
            assertThat(RacePreparationRule.preparacaoCurta(hoje.plusDays(1), hoje)).isFalse();
        }

        @Test
        @DisplayName("sem início derivado não é curta")
        void semInicio() {
            assertThat(RacePreparationRule.preparacaoCurta(null, hoje)).isFalse();
        }
    }
}
