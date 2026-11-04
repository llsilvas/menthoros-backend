package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.services.helper.ZonaTreinoService.ZonaPace;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Quantização do pace de limiar (150..720 s/km)")
class PaceLimiarQuantizacaoTest {

    private static final double[][] FATORES = {
            {1.15, 1.25}, {1.05, 1.15}, {1.00, 1.05}, {0.95, 1.00}, {0.90, 0.95}, {0.82, 0.90}
    };

    private final ZonaTreinoService zonaTreinoService = new ZonaTreinoService();

    static IntStream segundos() {
        return IntStream.rangeClosed(150, 720);
    }

    private static BigDecimal paraMinutos(int segKm) {
        return BigDecimal.valueOf(segKm).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    private static int paraSegundos(BigDecimal minutosDecimais) {
        return minutosDecimais.multiply(BigDecimal.valueOf(60)).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    @ParameterizedTest(name = "{0} s/km")
    @MethodSource("segundos")
    @DisplayName("ida e volta da âncora devolve o mesmo segundo")
    void idaEVolta(int segKm) {
        assertThat(paraSegundos(paraMinutos(segKm))).isEqualTo(segKm);
    }

    @ParameterizedTest(name = "{0} s/km")
    @MethodSource("segundos")
    @DisplayName("cada borda de zona fica a no máximo 1 s do cálculo direto")
    void bordasDentroDeUmSegundo(int segKm) {
        List<ZonaPace> zonas = zonaTreinoService.calcularZonasPace(paraMinutos(segKm));

        for (int i = 0; i < FATORES.length; i++) {
            long diretoMin = Math.round(segKm * FATORES[i][0]);
            long diretoMax = Math.round(segKm * FATORES[i][1]);
            assertThat((long) paraSegundos(zonas.get(i).paceMin())).isBetween(diretoMin - 1, diretoMin + 1);
            assertThat((long) paraSegundos(zonas.get(i).paceMax())).isBetween(diretoMax - 1, diretoMax + 1);
        }
    }
}
