package br.com.menthoros.backend.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code Prova.distancia} é persistida por ordinal: a ordem das constantes é contrato de banco.
 */
@DisplayName("DistanciaProva")
class DistanciaProvaTest {

    @Test
    @DisplayName("ordinais das distâncias padrão permanecem 0..3 e CUSTOMIZADA é a última")
    void ordinaisPreservados() {
        assertThat(DistanciaProva.KM_5.ordinal()).isZero();
        assertThat(DistanciaProva.KM_10.ordinal()).isEqualTo(1);
        assertThat(DistanciaProva.KM_21.ordinal()).isEqualTo(2);
        assertThat(DistanciaProva.KM_42.ordinal()).isEqualTo(3);
        assertThat(DistanciaProva.CUSTOMIZADA.ordinal()).isEqualTo(4);
        assertThat(DistanciaProva.values()).hasSize(5);
    }

    @Test
    @DisplayName("ordinal 2 gravado continua lendo KM_21")
    void ordinalDoisLeMeia() {
        assertThat(DistanciaProva.values()[2]).isEqualTo(DistanciaProva.KM_21);
    }
}
