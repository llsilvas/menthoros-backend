package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.services.helper.ZonaTreinoService.ZonaPace;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Calcula zonas de pace ajustadas pelo TSB atual do atleta.
 *
 * <p>Reutiliza {@link ZonaTreinoService#calcularZonasPace(BigDecimal)} como base
 * e aplica penalidades de pace em segundos/km conforme o estado de fadiga (TSB).</p>
 *
 * <p>Tabela de ajuste:</p>
 * <ul>
 *   <li>TSB &ge; 0: sem penalidade</li>
 *   <li>-10 &le; TSB &lt; 0: +3 seg/km</li>
 *   <li>-20 &le; TSB &lt; -10: +7 seg/km</li>
 *   <li>TSB &lt; -20: +12 seg/km</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class PaceZoneCalculator {

    private final ZonaTreinoService zonaTreinoService;

    public record ZonasAjustadas(List<ZonaPace> zonas, int ajusteSegundos) {}

    /**
     * Calcula as zonas de pace com ajuste de TSB aplicado.
     *
     * @param paceLimiar pace limiar do atleta em decimal minutos (ex: 5.00 = 5:00/km)
     * @param tsb        Training Stress Balance atual (pode ser null)
     * @return zonas ajustadas e o ajuste aplicado em seg/km (0 se TSB nulo ou positivo)
     */
    public ZonasAjustadas calcularZonasAjustadasPorTsb(BigDecimal paceLimiar, Double tsb) {
        List<ZonaPace> zonasBase = zonaTreinoService.calcularZonasPace(paceLimiar);
        int ajusteSegundos = calcularAjusteSegundos(tsb);

        if (ajusteSegundos == 0) {
            return new ZonasAjustadas(zonasBase, 0);
        }

        BigDecimal ajusteMinutos = BigDecimal.valueOf(ajusteSegundos)
                .divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP);

        List<ZonaPace> zonasAjustadas = zonasBase.stream()
                .map(zona -> new ZonaPace(
                        zona.numero(),
                        zona.nome(),
                        zona.paceMin().add(ajusteMinutos).setScale(2, RoundingMode.HALF_UP),
                        zona.paceMax().add(ajusteMinutos).setScale(2, RoundingMode.HALF_UP)
                ))
                .toList();

        return new ZonasAjustadas(zonasAjustadas, ajusteSegundos);
    }

    /**
     * Formata a nota de ajuste de TSB para inclusão no prompt.
     *
     * <p>Retorna string vazia se não há penalidade (TSB nulo ou &ge; 0).</p>
     */
    public String formatarAvisoAjuste(Double tsb) {
        int ajusteSegundos = calcularAjusteSegundos(tsb);
        if (ajusteSegundos == 0) return "";

        return String.format(
                "⚠️ **Ajuste por fadiga:** TSB atual = %.1f → paces com penalidade de **+%d seg/km** " +
                "nas zonas acima. Prescrever dentro das faixas indicadas.\n",
                tsb, ajusteSegundos
        );
    }

    // ===== Helpers =====

    /**
     * Calcula o ajuste de pace em segundos/km com base no TSB.
     *
     * @param tsb valor do TSB (pode ser null)
     * @return segundos de penalidade por km (0 se sem penalidade)
     */
    int calcularAjusteSegundos(Double tsb) {
        if (tsb == null || tsb >= 0) return 0;
        if (tsb < -20) return 12;
        if (tsb < -10) return 7;
        return 3; // -10 <= tsb < 0
    }
}
