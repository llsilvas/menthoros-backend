package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.services.helper.ZonaTreinoService.ZonaPace;
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
import static org.mockito.Mockito.when;

/**
 * Testes semânticos para PaceZoneCalculator.
 *
 * <p>Verifica que o ajuste de pace usa {@code tsbProntidaoAtual} (pré-treino)
 * e NÃO {@code tsbAtual} (pós-carga).</p>
 *
 * <p>TDD: testes escritos em RED antes da implementação.</p>
 */
@ExtendWith(MockitoExtension.class)
class PaceZoneSemanticaTest {

    @Mock
    private ZonaTreinoService zonaTreinoService;

    @InjectMocks
    private PaceZoneCalculator calculator;

    private static final BigDecimal PACE_LIMIAR = new BigDecimal("5.00"); // 5:00/km

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
    @DisplayName("ajuste de pace usa tsbProntidaoAtual — atleta fresco tem penalidade ZERO")
    void ajustePace_usaTsbProntidaoAtual_atletaFresco_semPenalidade() {
        when(zonaTreinoService.calcularZonasPace(any())).thenReturn(zonasBase());

        // tsbProntidaoAtual = +10 (fresco) → sem penalidade de pace
        // tsbAtual = -15 (pós-carga fatigado) → sem correção aplicaria +7 seg/km
        var resultado = calculator.calcularZonasAjustadasPorTsbProntidao(PACE_LIMIAR, +10.0);

        assertThat(resultado.ajusteSegundos())
                .as("TSB prontidão positivo (+10) não deve gerar penalidade de pace")
                .isZero();
        assertThat(resultado.zonas().get(0).paceMin())
                .as("Zona 1 não deve ter ajuste quando prontidão é positiva")
                .isEqualByComparingTo(new BigDecimal("5.75"));
    }

    @Test
    @DisplayName("ajuste de pace usa tsbProntidaoAtual — prontidão negativa aplica penalidade")
    void ajustePace_usaTsbProntidaoAtual_prontidaoNegativa_aplicaPenalidade() {
        when(zonaTreinoService.calcularZonasPace(any())).thenReturn(zonasBase());

        // tsbProntidaoAtual = -15 → deve aplicar penalidade de +7 seg/km
        var resultado = calculator.calcularZonasAjustadasPorTsbProntidao(PACE_LIMIAR, -15.0);

        assertThat(resultado.ajusteSegundos())
                .as("TSB prontidão -15 deve gerar penalidade de +7 seg/km")
                .isEqualTo(7);
    }

    @Test
    @DisplayName("ajuste de pace: prontidão fresca (+10) gera pace mais rápido que prontidão fatigada (-15)")
    void ajustePace_frescoMaisRapidoQueFatigado() {
        when(zonaTreinoService.calcularZonasPace(any())).thenReturn(zonasBase());

        var resultadoFresco = calculator.calcularZonasAjustadasPorTsbProntidao(PACE_LIMIAR, +10.0);
        when(zonaTreinoService.calcularZonasPace(any())).thenReturn(zonasBase());
        var resultadoFatigado = calculator.calcularZonasAjustadasPorTsbProntidao(PACE_LIMIAR, -15.0);

        BigDecimal paceFresco = resultadoFresco.zonas().get(0).paceMin();
        BigDecimal paceFatigado = resultadoFatigado.zonas().get(0).paceMin();

        assertThat(paceFresco)
                .as("Atleta fresco (prontidão +10) deve ter pace menor (mais rápido) que fatigado (-15)")
                .isLessThan(paceFatigado);
    }

    @Test
    @DisplayName("formatarAvisoAjusteProntidao usa tsbProntidaoAtual — sem penalidade quando fresco")
    void formatarAvisoAjusteProntidao_semPenalidade_quando_fresco() {
        String aviso = calculator.formatarAvisoAjusteProntidao(+10.0);

        assertThat(aviso)
                .as("Atleta fresco não deve ter aviso de penalidade")
                .isEmpty();
    }

    @Test
    @DisplayName("formatarAvisoAjusteProntidao usa tsbProntidaoAtual — com penalidade quando fatigado")
    void formatarAvisoAjusteProntidao_comPenalidade_quando_fatigado() {
        String aviso = calculator.formatarAvisoAjusteProntidao(-20.0);

        assertThat(aviso)
                .as("Atleta fatigado (prontidão -20) deve ter aviso de penalidade")
                .isNotEmpty()
                .contains("-20");
    }
}
