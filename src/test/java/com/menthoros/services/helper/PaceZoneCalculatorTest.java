package com.menthoros.services.helper;

import com.menthoros.services.helper.ZonaTreinoService.ZonaPace;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaceZoneCalculatorTest {

    @Mock
    private ZonaTreinoService zonaTreinoService;

    @InjectMocks
    private PaceZoneCalculator paceZoneCalculator;

    private static final BigDecimal PACE_LIMIAR = new BigDecimal("5.00"); // 5:00/km

    /** Zonas base para paceLimiar = 5:00/km (sem ajuste TSB). */
    private List<ZonaPace> zonasBase() {
        return List.of(
                new ZonaPace(1, "Recuperação", new BigDecimal("5.75"), new BigDecimal("6.25")),
                new ZonaPace(2, "Aeróbico",    new BigDecimal("5.25"), new BigDecimal("5.75")),
                new ZonaPace(3, "Tempo",       new BigDecimal("4.90"), new BigDecimal("5.25")),
                new ZonaPace(4, "Limiar",      new BigDecimal("4.75"), new BigDecimal("5.00")),
                new ZonaPace(5, "VO2max",      new BigDecimal("4.50"), new BigDecimal("4.85")),
                new ZonaPace(6, "Sprint",      new BigDecimal("4.10"), new BigDecimal("4.50"))
        );
    }

    @Test
    @DisplayName("Deve manter zonas iguais quando TSB é positivo")
    void deveMantarZonasIguaisComTsbPositivo() {
        when(zonaTreinoService.calcularZonasPace(PACE_LIMIAR)).thenReturn(zonasBase());

        var resultado = paceZoneCalculator.calcularZonasAjustadasPorTsb(PACE_LIMIAR, 5.0);

        assertThat(resultado.ajusteSegundos()).isZero();
        assertThat(resultado.zonas()).hasSize(6);
        assertThat(resultado.zonas().get(0).paceMin())
                .isEqualByComparingTo(new BigDecimal("5.75"));
    }

    @Test
    @DisplayName("Deve aplicar penalidade de +7 seg/km quando TSB = -15")
    void deveAplicarPenalidadeComTsbNegativo() {
        when(zonaTreinoService.calcularZonasPace(PACE_LIMIAR)).thenReturn(zonasBase());

        var resultado = paceZoneCalculator.calcularZonasAjustadasPorTsb(PACE_LIMIAR, -15.0);

        assertThat(resultado.ajusteSegundos()).isEqualTo(7);
        // 7 seg = 7/60 = 0.1167 min. Z1.paceMin = 5.75 + 0.1167 = 5.8667 → 5.87
        assertThat(resultado.zonas().get(0).paceMin())
                .isEqualByComparingTo(new BigDecimal("5.87"));
        assertThat(resultado.zonas().get(0).paceMax())
                .isEqualByComparingTo(new BigDecimal("6.37"));
    }

    @Test
    @DisplayName("Deve aplicar penalidade máxima de +12 seg/km quando TSB = -25")
    void deveAplicarPenalidadeMaximaComTsbMuitoNegativo() {
        when(zonaTreinoService.calcularZonasPace(PACE_LIMIAR)).thenReturn(zonasBase());

        var resultado = paceZoneCalculator.calcularZonasAjustadasPorTsb(PACE_LIMIAR, -25.0);

        assertThat(resultado.ajusteSegundos()).isEqualTo(12);
        // 12 seg = 12/60 = 0.2000 min. Z1.paceMin = 5.75 + 0.20 = 5.95
        assertThat(resultado.zonas().get(0).paceMin())
                .isEqualByComparingTo(new BigDecimal("5.95"));
    }

    @Test
    @DisplayName("Deve retornar zonas válidas quando paceLimiar é nulo (delegado a ZonaTreinoService)")
    void deveRetornarZonasValidas_QuandoPaceLimiarNulo() {
        List<ZonaPace> zonasZeradas = List.of(
                new ZonaPace(1, "Recuperação", BigDecimal.ZERO, BigDecimal.ZERO),
                new ZonaPace(2, "Aeróbico", BigDecimal.ZERO, BigDecimal.ZERO),
                new ZonaPace(3, "Tempo", BigDecimal.ZERO, BigDecimal.ZERO),
                new ZonaPace(4, "Limiar", BigDecimal.ZERO, BigDecimal.ZERO),
                new ZonaPace(5, "VO2max", BigDecimal.ZERO, BigDecimal.ZERO),
                new ZonaPace(6, "Sprint", BigDecimal.ZERO, BigDecimal.ZERO)
        );
        when(zonaTreinoService.calcularZonasPace(null)).thenReturn(zonasZeradas);

        var resultado = paceZoneCalculator.calcularZonasAjustadasPorTsb(null, 0.0);

        // Sem exceção, retorna zonas (possivelmente zeradas conforme ZonaTreinoService)
        assertThat(resultado.zonas()).isNotNull();
        assertThat(resultado.ajusteSegundos()).isZero();
    }

    @Test
    @DisplayName("Deve reutilizar resultado do ZonaTreinoService sem reimplementar percentuais")
    void deveReutilizarResultadoDoZonaTreinoService() {
        when(zonaTreinoService.calcularZonasPace(any())).thenReturn(zonasBase());

        paceZoneCalculator.calcularZonasAjustadasPorTsb(PACE_LIMIAR, -5.0);

        // Verifica que a delegação ao ZonaTreinoService ocorreu exatamente uma vez
        verify(zonaTreinoService, times(1)).calcularZonasPace(PACE_LIMIAR);
        verifyNoMoreInteractions(zonaTreinoService);
    }

    // ======================== calcularAjusteSegundos (granular) ========================

    @Test
    @DisplayName("Deve calcular ajuste 0 para TSB nulo")
    void deveCalcularAjusteZeroParaTsbNulo() {
        assertThat(paceZoneCalculator.calcularAjusteSegundos(null)).isZero();
    }

    @Test
    @DisplayName("Deve calcular ajuste correto para fronteiras do TSB")
    void deveCalcularAjusteCorretoParaFronteiras() {
        assertThat(paceZoneCalculator.calcularAjusteSegundos(0.0)).isZero();
        assertThat(paceZoneCalculator.calcularAjusteSegundos(-1.0)).isEqualTo(3);
        assertThat(paceZoneCalculator.calcularAjusteSegundos(-10.1)).isEqualTo(7);
        assertThat(paceZoneCalculator.calcularAjusteSegundos(-20.1)).isEqualTo(12);
    }
}
