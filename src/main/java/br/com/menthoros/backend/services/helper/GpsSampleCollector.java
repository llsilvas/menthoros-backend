package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.fit.FitGpsSample;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Coleta pontos GPS do stream do .fit com memória limitada (add-activity-gps-trace, D3).
 *
 * <p>Decimação on-line: um ponto só entra se estiver a pelo menos {@code stepM} metros do último
 * ponto guardado. Quando a lista atinge {@link #MAX_POINTS}, o passo dobra e a lista atual é
 * re-decimada no mesmo passo — assim o pior caso de memória é fixo, qualquer que seja o tamanho da
 * atividade ou o ruído do GPS. Não é thread-safe: um coletor por parse.
 */
public final class GpsSampleCollector {

    /** Teto de pontos em memória durante o parse (~800 KB). O trajeto persistido é bem menor (D3). */
    public static final int MAX_POINTS = 20_000;

    /** Passo inicial de decimação, em metros. */
    public static final double INITIAL_STEP_M = 5.0;

    /** Sentinela do SDK da Garmin para coordenada inválida ({@code 0x7FFFFFFF}). */
    static final int INVALID_SEMICIRCLES = Integer.MAX_VALUE;

    private static final double SEMICIRCLES_TO_DEGREES = 180.0 / 2_147_483_648.0; // 2^31
    private static final double EARTH_RADIUS_M = 6_371_000.0;

    /** Ponto guardado com a distância acumulada, usada só para re-decimar. */
    private record Point(long timestampSeg, double lat, double lng, @Nullable Integer fc, double distM) {}

    private final List<Point> points = new ArrayList<>();
    private double stepM = INITIAL_STEP_M;
    private double lastKeptDistM = Double.NEGATIVE_INFINITY;
    // Último ponto OFERECIDO (não o último guardado): a distância acumulada por haversine é a soma
    // dos trechos entre records consecutivos, senão o trecho desde o último guardado conta duas vezes.
    private double lastDistM = 0;
    private boolean hasLast = false;
    private double lastLat;
    private double lastLng;

    /**
     * Oferece um record ao coletor. Coordenadas nulas ou com a sentinela de inválido são ignoradas.
     *
     * @param distanceM distância acumulada do record ({@code RecordMesg.getDistance()}), nula quando
     *                  o arquivo não traz; aí a distância é estimada por haversine
     */
    public void offer(long timestampSeg, @Nullable Integer latSemicircles, @Nullable Integer lngSemicircles,
                      @Nullable Integer fc, @Nullable Float distanceM) {
        if (!valid(latSemicircles) || !valid(lngSemicircles)) {
            return;
        }
        double lat = latSemicircles * SEMICIRCLES_TO_DEGREES;
        double lng = lngSemicircles * SEMICIRCLES_TO_DEGREES;

        double distM;
        if (distanceM != null) {
            distM = distanceM;
        } else if (!hasLast) {
            distM = 0;
        } else {
            distM = lastDistM + haversineM(lastLat, lastLng, lat, lng);
        }
        lastDistM = distM;
        lastLat = lat;
        lastLng = lng;
        hasLast = true;

        if (distM - lastKeptDistM < stepM) {
            return;
        }
        if (points.size() >= MAX_POINTS) {
            doubleStepAndRedecimate();
            if (distM - lastKeptDistM < stepM) {
                return;
            }
        }
        points.add(new Point(timestampSeg, lat, lng, fc, distM));
        lastKeptDistM = distM;
    }

    /** Pontos coletados, em ordem de arquivo. Vazio quando o arquivo não tem GPS válido. */
    public List<FitGpsSample> samples() {
        return points.stream()
                .map(p -> new FitGpsSample(p.timestampSeg(), p.lat(), p.lng(), p.fc()))
                .toList();
    }

    /** Passo de decimação em vigor, em metros — exposto para teste. */
    double stepM() {
        return stepM;
    }

    private void doubleStepAndRedecimate() {
        stepM *= 2;
        List<Point> kept = new ArrayList<>(points.size() / 2 + 1);
        double lastKept = Double.NEGATIVE_INFINITY;
        for (Point p : points) {
            if (p.distM() - lastKept >= stepM) {
                kept.add(p);
                lastKept = p.distM();
            }
        }
        points.clear();
        points.addAll(kept);
        lastKeptDistM = lastKept;
    }

    private static boolean valid(@Nullable Integer semicircles) {
        return semicircles != null && semicircles != INVALID_SEMICIRCLES;
    }

    static double haversineM(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }
}
