package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.entity.Atleta;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Serviço dedicado ao cálculo de zonas de treino (FC e Pace).
 *
 * <p>Centraliza a lógica de cálculo das 6 zonas de intensidade baseadas nos
 * limiares fisiológicos do atleta. As zonas de FC usam o modelo LTHR (Lactate
 * Threshold Heart Rate) de Friel, onde a <strong>FC Limiar</strong> é a única
 * âncora de todas as zonas:</p>
 *
 * <table border="1">
 *   <caption>Zonas de FC — modelo LTHR (Friel)</caption>
 *   <tr><th>Zona</th><th>% FC Limiar</th><th>Descrição</th></tr>
 *   <tr><td>Z1 (Recuperação)</td><td>75–85%</td><td>Recuperação ativa</td></tr>
 *   <tr><td>Z2 (Aeróbico)</td><td>85–89%</td><td>Base aeróbica</td></tr>
 *   <tr><td>Z3 (Tempo)</td><td>89–94%</td><td>Tempo moderado / SubLimiar</td></tr>
 *   <tr><td>Z4 (Limiar)</td><td>94–100%</td><td>Limiar anaeróbico</td></tr>
 *   <tr><td>Z5 (VO2max)</td><td>100–106%</td><td>VO2max / supralimiar</td></tr>
 * </table>
 *
 * <p>Zonas de pace usam {@code paceLimiar} como referência (já era correto).</p>
 *
 * <p>Z6 existe apenas para pace. Em esforços anaeróbicos curtos (&lt;1 min),
 * a FC não estabiliza a tempo (cardiac lag), tornando a medição por FC
 * imprecisa. Para FC, esforços de sprint são classificados como Z5.</p>
 */
@Component
public class ZonaTreinoService {

    public record ZonaFC(int numero, String nome, int fcMin, int fcMax) {}

    public record ZonaPace(int numero, String nome, BigDecimal paceMin, BigDecimal paceMax) {}

    public record ZonaCompleta(int numero, String nome, ZonaFC fc, ZonaPace pace) {}

    // FC: 5 zonas — modelo LTHR (Friel), todas baseadas em % do FC Limiar
    private static final double[][] FC_LIMIAR_PERCENTUAIS = {
            {0.75, 0.85}, // Z1: Recuperação
            {0.85, 0.89}, // Z2: Aeróbico
            {0.89, 0.94}, // Z3: Tempo
            {0.94, 1.00}, // Z4: Limiar
            {1.00, 1.06}, // Z5: VO2max (supralimiar — cap prático de 106%)
    };

    // Pace: 6 zonas (Z6 = sprint/anaeróbico)
    private static final double[][] PACE_FATORES = {
            {1.15, 1.25}, // Z1 (mais lento = mais fácil)
            {1.05, 1.15}, // Z2
            {0.98, 1.05}, // Z3
            {0.95, 1.00}, // Z4
            {0.90, 0.97}, // Z5
            {0.82, 0.90}, // Z6 (sprint, mais rápido que VO2max)
    };

    private static final String[] NOMES_ZONAS = {
            "Recuperação", "Aeróbico", "Tempo", "Limiar", "VO2max", "Sprint"
    };

    /**
     * Calcula as 6 zonas completas (FC + Pace) para um atleta.
     * Z6 (Sprint) tem FC equivalente a Z5 (cardiac lag impede medição precisa).
     */
    public List<ZonaCompleta> calcularZonas(Atleta atleta) {
        Integer fcMax = atleta.getFcMaximaCalculada();
        Integer fcLimiar = atleta.getFcLimiarCalculada();
        BigDecimal paceLimiar = atleta.getPaceLimiar();

        List<ZonaFC> zonasFC = calcularZonasFC(fcMax, fcLimiar);
        List<ZonaPace> zonasPace = calcularZonasPace(paceLimiar);

        return List.of(
                new ZonaCompleta(1, NOMES_ZONAS[0], zonasFC.get(0), zonasPace.get(0)),
                new ZonaCompleta(2, NOMES_ZONAS[1], zonasFC.get(1), zonasPace.get(1)),
                new ZonaCompleta(3, NOMES_ZONAS[2], zonasFC.get(2), zonasPace.get(2)),
                new ZonaCompleta(4, NOMES_ZONAS[3], zonasFC.get(3), zonasPace.get(3)),
                new ZonaCompleta(5, NOMES_ZONAS[4], zonasFC.get(4), zonasPace.get(4)),
                new ZonaCompleta(6, NOMES_ZONAS[5], zonasFC.get(4), zonasPace.get(5)) // Z6 FC = Z5 FC
        );
    }

    /**
     * Calcula as 5 zonas de FC usando {@code fcLimiar} (LTHR) como base em todas as zonas.
     * O parâmetro {@code fcMaxima} é mantido para compatibilidade de chamada, mas não é usado.
     * Apenas 5 zonas — Z6 não se aplica a FC (cardiac lag em sprints).
     */
    public List<ZonaFC> calcularZonasFC(Integer fcMaxima, Integer fcLimiar) {
        return List.of(
                calcularZonaFC(1, fcLimiar, FC_LIMIAR_PERCENTUAIS[0]),
                calcularZonaFC(2, fcLimiar, FC_LIMIAR_PERCENTUAIS[1]),
                calcularZonaFC(3, fcLimiar, FC_LIMIAR_PERCENTUAIS[2]),
                calcularZonaFC(4, fcLimiar, FC_LIMIAR_PERCENTUAIS[3]),
                calcularZonaFC(5, fcLimiar, FC_LIMIAR_PERCENTUAIS[4])
        );
    }

    /**
     * Calcula as 6 zonas de pace baseadas no pace limiar.
     * Z6 (Sprint) = pace abaixo de 90% do limiar (esforço anaeróbico).
     */
    public List<ZonaPace> calcularZonasPace(BigDecimal paceLimiar) {
        return List.of(
                calcularZonaPace(1, paceLimiar, PACE_FATORES[0]),
                calcularZonaPace(2, paceLimiar, PACE_FATORES[1]),
                calcularZonaPace(3, paceLimiar, PACE_FATORES[2]),
                calcularZonaPace(4, paceLimiar, PACE_FATORES[3]),
                calcularZonaPace(5, paceLimiar, PACE_FATORES[4]),
                calcularZonaPace(6, paceLimiar, PACE_FATORES[5])
        );
    }

    /**
     * Identifica em qual zona (1-5) está uma FC específica.
     * Retorna 0 se abaixo de Z1.
     */
    public int identificarZonaFC(Integer fc, Atleta atleta) {
        if (fc == null) return 0;

        List<ZonaFC> zonas = calcularZonasFC(atleta.getFcMaximaCalculada(), atleta.getFcLimiarCalculada());
        for (int i = zonas.size() - 1; i >= 0; i--) {
            if (fc >= zonas.get(i).fcMin()) {
                return zonas.get(i).numero();
            }
        }
        return 0;
    }

    /**
     * Identifica em qual zona (1-6) está um pace específico.
     * Pace menor = mais rápido = zona maior.
     */
    public int identificarZonaPace(BigDecimal pace, Atleta atleta) {
        if (pace == null || atleta.getPaceLimiar() == null) return 0;

        List<ZonaPace> zonas = calcularZonasPace(atleta.getPaceLimiar());
        for (int i = zonas.size() - 1; i >= 0; i--) {
            if (pace.compareTo(zonas.get(i).paceMax()) <= 0) {
                return zonas.get(i).numero();
            }
        }
        return 0;
    }

    private ZonaFC calcularZonaFC(int numero, Integer fcBase, double[] percentuais) {
        if (fcBase == null) {
            return new ZonaFC(numero, NOMES_ZONAS[numero - 1], 0, 0);
        }
        int fcMin = (int) Math.round(fcBase * percentuais[0]);
        int fcMax = (int) Math.round(fcBase * percentuais[1]);
        return new ZonaFC(numero, NOMES_ZONAS[numero - 1], fcMin, fcMax);
    }

    private ZonaPace calcularZonaPace(int numero, BigDecimal paceLimiar, double[] fatores) {
        if (paceLimiar == null) {
            return new ZonaPace(numero, NOMES_ZONAS[numero - 1], BigDecimal.ZERO, BigDecimal.ZERO);
        }
        BigDecimal paceMin = paceLimiar.multiply(BigDecimal.valueOf(fatores[0]))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal paceMax = paceLimiar.multiply(BigDecimal.valueOf(fatores[1]))
                .setScale(2, RoundingMode.HALF_UP);
        return new ZonaPace(numero, NOMES_ZONAS[numero - 1], paceMin, paceMax);
    }
}
