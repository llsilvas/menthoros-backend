package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.fit.FitGpsSample;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Simplifica o trajeto GPS já decimado para o que o card precisa (add-activity-gps-trace, D3):
 * Douglas-Peucker com tolerância em metros e teto de pontos. Se o DP ainda passar do teto, a
 * tolerância dobra até caber. Primeiro e último ponto sempre ficam; menos de 2 pontos → vazio.
 *
 * <p>Função pura, sem estado. A distância ponto-segmento usa projeção equirretangular local
 * (bastante precisa na escala de um trajeto de corrida).
 */
public final class GpsTraceSimplifier {

    /** Tolerância inicial do Douglas-Peucker, em metros (Q2: validar com arquivos reais). */
    public static final double TOLERANCE_M = 10.0;

    /** Teto de pontos do trajeto persistido. */
    public static final int MAX_POINTS = 400;

    private static final double METERS_PER_DEGREE_LAT = 111_320.0;

    private GpsTraceSimplifier() {}

    public static List<FitGpsSample> simplify(List<FitGpsSample> samples) {
        return simplify(samples, TOLERANCE_M, MAX_POINTS);
    }

    static List<FitGpsSample> simplify(List<FitGpsSample> samples, double toleranceM, int maxPoints) {
        if (samples.size() < 2) {
            return List.of();
        }
        double tol = toleranceM;
        List<FitGpsSample> result = douglasPeucker(samples, tol);
        while (result.size() > maxPoints) {
            tol *= 2;
            result = douglasPeucker(result, tol);
        }
        return result;
    }

    /** DP iterativo (pilha), para não estourar a recursão em trajetos longos. */
    private static List<FitGpsSample> douglasPeucker(List<FitGpsSample> pts, double toleranceM) {
        int n = pts.size();
        boolean[] keep = new boolean[n];
        keep[0] = true;
        keep[n - 1] = true;

        double cosLat = Math.cos(Math.toRadians(pts.get(0).lat()));
        Deque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[]{0, n - 1});
        while (!stack.isEmpty()) {
            int[] seg = stack.pop();
            int a = seg[0], b = seg[1];
            if (b - a < 2) continue;
            double maxDist = -1;
            int idx = -1;
            for (int i = a + 1; i < b; i++) {
                double d = distanceToSegmentM(pts.get(i), pts.get(a), pts.get(b), cosLat);
                if (d > maxDist) {
                    maxDist = d;
                    idx = i;
                }
            }
            if (maxDist > toleranceM) {
                keep[idx] = true;
                stack.push(new int[]{a, idx});
                stack.push(new int[]{idx, b});
            }
        }
        List<FitGpsSample> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (keep[i]) out.add(pts.get(i));
        }
        return out;
    }

    private static double distanceToSegmentM(FitGpsSample p, FitGpsSample a, FitGpsSample b, double cosLat) {
        double px = (p.lng() - a.lng()) * cosLat * METERS_PER_DEGREE_LAT;
        double py = (p.lat() - a.lat()) * METERS_PER_DEGREE_LAT;
        double bx = (b.lng() - a.lng()) * cosLat * METERS_PER_DEGREE_LAT;
        double by = (b.lat() - a.lat()) * METERS_PER_DEGREE_LAT;
        double len2 = bx * bx + by * by;
        if (len2 == 0) {
            return Math.hypot(px, py);
        }
        double t = Math.max(0, Math.min(1, (px * bx + py * by) / len2));
        return Math.hypot(px - t * bx, py - t * by);
    }
}
