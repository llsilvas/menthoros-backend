package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.enums.DistanciaProva;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ProvaEnricher")
class ProvaEnricherTest {

    private final ProvaEnricher enricher = new ProvaEnricher();

    @Nested
    @DisplayName("aplicarDerivados")
    class AplicarDerivados {

        @Test
        @DisplayName("maratona em 6/dez recebe 16 semanas, início em 16/ago e 42,2 km")
        void maratonaPadrao() {
            Prova prova = Prova.builder()
                    .distancia(DistanciaProva.KM_42)
                    .dataProva(LocalDate.of(2026, 12, 6))
                    .build();

            enricher.aplicarDerivados(prova);

            assertThat(prova.getSemanasPreparacao()).isEqualTo(16);
            assertThat(prova.getInicioPreparacao()).isEqualTo(LocalDate.of(2026, 8, 16));
            assertThat(prova.getDistanciaKm()).isEqualByComparingTo(new BigDecimal("42.2"));
        }

        @Test
        @DisplayName("sobrescreve semanas e início vindos do cliente")
        void sobrescreveValoresDoCliente() {
            Prova prova = Prova.builder()
                    .distancia(DistanciaProva.KM_10)
                    .dataProva(LocalDate.of(2026, 12, 6))
                    .semanasPreparacao(2)
                    .inicioPreparacao(LocalDate.of(2026, 12, 1))
                    .build();

            enricher.aplicarDerivados(prova);

            assertThat(prova.getSemanasPreparacao()).isEqualTo(10);
            assertThat(prova.getInicioPreparacao()).isEqualTo(LocalDate.of(2026, 9, 27));
        }

        @Test
        @DisplayName("distância padrão com distanciaKm informado mantém o valor informado")
        void mantemDistanciaKmInformada() {
            Prova prova = Prova.builder()
                    .distancia(DistanciaProva.KM_21)
                    .distanciaKm(new BigDecimal("21.0975"))
                    .dataProva(LocalDate.of(2026, 12, 6))
                    .build();

            enricher.aplicarDerivados(prova);

            assertThat(prova.getDistanciaKm()).isEqualByComparingTo(new BigDecimal("21.0975"));
            assertThat(prova.getSemanasPreparacao()).isEqualTo(12);
        }

        @Test
        @DisplayName("customizada de 30 km usa a faixa de 21 km e não recebe nominal")
        void customizadaUsaFaixa() {
            Prova prova = Prova.builder()
                    .distancia(DistanciaProva.CUSTOMIZADA)
                    .distanciaKm(new BigDecimal("30"))
                    .dataProva(LocalDate.of(2026, 12, 6))
                    .build();

            enricher.aplicarDerivados(prova);

            assertThat(prova.getDistanciaKm()).isEqualByComparingTo(new BigDecimal("30"));
            assertThat(prova.getSemanasPreparacao()).isEqualTo(12);
        }

        @Test
        @DisplayName("customizada sem quilometragem é rejeitada")
        void customizadaSemKm() {
            Prova prova = Prova.builder()
                    .distancia(DistanciaProva.CUSTOMIZADA)
                    .dataProva(LocalDate.of(2026, 12, 6))
                    .build();

            assertThatThrownBy(() -> enricher.aplicarDerivados(prova))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("prova nula ou sem data/distância é rejeitada")
        void entradaInvalida() {
            assertThatThrownBy(() -> enricher.aplicarDerivados(null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> enricher.aplicarDerivados(Prova.builder().distancia(DistanciaProva.KM_5).build()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> enricher.aplicarDerivados(Prova.builder().dataProva(LocalDate.now()).build()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
