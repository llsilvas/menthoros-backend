package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.fit.FitGpsSample;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GpsTraceSimplifier")
class GpsTraceSimplifierTest {

    private static final double LAT0 = -23.55;
    private static final double LNG0 = -46.63;
    private static final double DEG_PER_M_LAT = 1 / 111_320.0;

    @Test
    @DisplayName("menos de 2 pontos → vazio")
    void lessThanTwoPointsIsEmpty() {
        assertThat(GpsTraceSimplifier.simplify(List.of())).isEmpty();
        assertThat(GpsTraceSimplifier.simplify(List.of(p(0, 0)))).isEmpty();
    }

    @Test
    @DisplayName("reta com ruído menor que a tolerância vira só os dois extremos")
    void straightLineCollapsesToEndpoints() {
        List<FitGpsSample> line = new ArrayList<>();
        for (int i = 0; i <= 100; i++) {
            // 10 m por ponto ao norte, com 2 m de ruído lateral alternado (< 10 m de tolerância).
            line.add(p(i * 10, (i % 2 == 0 ? 2 : -2)));
        }

        List<FitGpsSample> s = GpsTraceSimplifier.simplify(line);

        assertThat(s).hasSize(2);
        assertThat(s.get(0)).isEqualTo(line.get(0));
        assertThat(s.get(1)).isEqualTo(line.get(100));
    }

    @Test
    @DisplayName("preserva uma curva de 90° (vértice fora da tolerância)")
    void keepsCorner() {
        List<FitGpsSample> l = new ArrayList<>();
        for (int i = 0; i <= 50; i++) l.add(p(i * 10, 0));          // 500 m ao norte
        for (int i = 1; i <= 50; i++) l.add(p(500, i * 10));        // depois 500 m a leste

        List<FitGpsSample> s = GpsTraceSimplifier.simplify(l);

        assertThat(s).hasSize(3);
        assertThat(s.get(1).lat()).isCloseTo(l.get(50).lat(), org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    @DisplayName("acima do teto, dobra a tolerância até caber e mantém os extremos")
    void doublesToleranceUntilUnderCap() {
        // Zigue-zague de 20 m de amplitude a cada 15 m: todo ponto é vértice a 10 m de tolerância.
        List<FitGpsSample> zig = new ArrayList<>();
        for (int i = 0; i < 3000; i++) zig.add(p(i * 15, (i % 2 == 0 ? 20 : -20)));

        List<FitGpsSample> s = GpsTraceSimplifier.simplify(zig);

        assertThat(s).hasSizeLessThanOrEqualTo(GpsTraceSimplifier.MAX_POINTS).hasSizeGreaterThan(1);
        assertThat(s.get(0)).isEqualTo(zig.get(0));
        assertThat(s.get(s.size() - 1)).isEqualTo(zig.get(zig.size() - 1));
        // A ordem de arquivo é preservada.
        for (int i = 1; i < s.size(); i++) {
            assertThat(s.get(i).timestampSeg()).isGreaterThan(s.get(i - 1).timestampSeg());
        }
    }

    /** Ponto a {@code northM} metros ao norte e {@code eastM} a leste da origem. */
    private static FitGpsSample p(double northM, double eastM) {
        double lat = LAT0 + northM * DEG_PER_M_LAT;
        double lng = LNG0 + eastM * DEG_PER_M_LAT / Math.cos(Math.toRadians(LAT0));
        return new FitGpsSample((long) (northM + eastM), lat, lng, null);
    }
}
