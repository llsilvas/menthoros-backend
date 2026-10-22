package br.com.menthoros.backend.enums;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DispositivoMarcaTest {

    private static final Map<DispositivoMarca, Integer> PRIOR_ESPERADO = Map.of(
            DispositivoMarca.GARMIN, 1,
            DispositivoMarca.COROS, 2,
            DispositivoMarca.POLAR, 2,
            DispositivoMarca.SUUNTO, 2,
            DispositivoMarca.APPLE, 3,
            DispositivoMarca.AMAZFIT, 3,
            DispositivoMarca.SAMSUNG, 3,
            DispositivoMarca.OUTRO, 4);

    @ParameterizedTest
    @EnumSource(DispositivoMarca.class)
    void priorDeCadaMarca(DispositivoMarca marca) {
        assertThat(DispositivoMarca.values()).hasSize(PRIOR_ESPERADO.size());
        assertThat(marca.prior()).isEqualTo(PRIOR_ESPERADO.get(marca));
    }
}
