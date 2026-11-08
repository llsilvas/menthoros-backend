package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.fit.FitGpsSample;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("GpsSampleCollector")
class GpsSampleCollectorTest {

    // -23.55° S, -46.63° W (São Paulo) em semicírculos.
    private static final int LAT0 = (int) Math.round(-23.55 / (180.0 / 2_147_483_648.0));
    private static final int LNG0 = (int) Math.round(-46.63 / (180.0 / 2_147_483_648.0));
    // ~1 m de longitude nessa latitude ≈ 0,0000098°; em semicírculos ≈ 117.
    private static final int ONE_METER_LNG = 117;

    @Test
    @DisplayName("converte semicírculo em grau e mantém FC e timestamp")
    void convertsSemicirclesToDegrees() {
        GpsSampleCollector c = new GpsSampleCollector();
        c.offer(1_000, LAT0, LNG0, 142, 0f);

        List<FitGpsSample> s = c.samples();
        assertThat(s).hasSize(1);
        assertThat(s.get(0).lat()).isCloseTo(-23.55, within(1e-6));
        assertThat(s.get(0).lng()).isCloseTo(-46.63, within(1e-6));
        assertThat(s.get(0).fc()).isEqualTo(142);
        assertThat(s.get(0).timestampSeg()).isEqualTo(1_000);
    }

    @Test
    @DisplayName("ignora coordenada nula ou com a sentinela 0x7FFFFFFF e segue")
    void skipsInvalidCoordinates() {
        GpsSampleCollector c = new GpsSampleCollector();
        c.offer(0, null, LNG0, null, 0f);
        c.offer(1, LAT0, GpsSampleCollector.INVALID_SEMICIRCLES, null, 1f);
        c.offer(2, LAT0, LNG0, null, 10f);

        assertThat(c.samples()).extracting(FitGpsSample::timestampSeg).containsExactly(2L);
    }

    @Test
    @DisplayName("decima pela distância acumulada do record: só guarda a cada 5 m")
    void decimatesByRecordDistance() {
        GpsSampleCollector c = new GpsSampleCollector();
        for (int i = 0; i <= 20; i++) {
            c.offer(i, LAT0, LNG0 + i * ONE_METER_LNG, null, (float) i); // 1 m por record
        }

        assertThat(c.samples()).extracting(FitGpsSample::timestampSeg).containsExactly(0L, 5L, 10L, 15L, 20L);
    }

    @Test
    @DisplayName("sem distância no record, estima por haversine")
    void fallsBackToHaversine() {
        GpsSampleCollector c = new GpsSampleCollector();
        for (int i = 0; i <= 12; i++) {
            c.offer(i, LAT0, LNG0 + i * ONE_METER_LNG, null, null);
        }

        // ~1 m por record: guarda o 0 e depois a cada 5 records (tolerância de 1 pelo arredondamento).
        assertThat(c.samples()).hasSizeBetween(3, 4);
        assertThat(c.samples().get(0).timestampSeg()).isZero();
        assertThat(c.samples().get(1).timestampSeg()).isBetween(5L, 6L);
    }

    @Test
    @DisplayName("nunca passa do teto: dobra o passo e re-decima, preservando o primeiro ponto")
    void neverExceedsCapAndDoublesStep() {
        GpsSampleCollector c = new GpsSampleCollector();
        int n = 100_000;
        for (int i = 0; i < n; i++) {
            // 6 m por record: sem teto, a decimação de 5 m guardaria todos os 100k.
            c.offer(i, LAT0, LNG0 + i * 6 * ONE_METER_LNG, null, i * 6f);
        }

        List<FitGpsSample> s = c.samples();
        // Depois da última dobra a lista cai para ~metade e volta a crescer: fica entre MAX/2 e MAX.
        assertThat(s).hasSizeBetween(GpsSampleCollector.MAX_POINTS / 2, GpsSampleCollector.MAX_POINTS);
        assertThat(s.get(0).timestampSeg()).isZero();
        assertThat(s.get(s.size() - 1).timestampSeg()).isGreaterThan(n - 20L);
        // O passo só muda dobrando: 5 → 10 → 20 → 40…
        double ratio = c.stepM() / GpsSampleCollector.INITIAL_STEP_M;
        assertThat(ratio).isGreaterThan(1.0);
        assertThat(Math.log(ratio) / Math.log(2)).isCloseTo(Math.round(Math.log(ratio) / Math.log(2)), within(1e-9));
    }

    @Test
    @DisplayName("distância NaN ou que anda para trás é ignorada: cai no haversine (arquivo hostil)")
    void ignoresBogusRecordDistance() {
        GpsSampleCollector c = new GpsSampleCollector();
        c.offer(0, LAT0, LNG0, null, 0f);
        c.offer(1, LAT0, LNG0 + 2 * ONE_METER_LNG, null, Float.NaN);   // 2 m reais
        c.offer(2, LAT0, LNG0 + 4 * ONE_METER_LNG, null, -500f);       // anda para trás
        c.offer(3, LAT0, LNG0 + 6 * ONE_METER_LNG, null, 1e9f);        // salto absurdo, mas finito e crescente: aceito como está

        // 0 guardado; 1 e 2 ficam a < 5 m pelo haversine; 3 entra pela distância do arquivo.
        assertThat(c.samples()).extracting(FitGpsSample::timestampSeg).containsExactly(0L, 3L);
    }

    @Test
    @DisplayName("lat/lng fora da faixa são descartados")
    void dropsOutOfRangeCoordinates() {
        GpsSampleCollector c = new GpsSampleCollector();
        c.offer(0, Integer.MAX_VALUE - 1, LNG0, null, 0f); // ~180° de latitude
        c.offer(1, LAT0, LNG0, null, 10f);
        assertThat(c.samples()).extracting(FitGpsSample::timestampSeg).containsExactly(1L);
    }

    @Test
    @DisplayName("saltos maiores que o passo num arquivo hostil nunca passam do teto")
    void hostileJumpsNeverExceedCap() {
        GpsSampleCollector c = new GpsSampleCollector();
        for (int i = 0; i < 60_000; i++) {
            // 1 km por record: a re-decimação não remove nada até o passo passar de 1 km.
            c.offer(i, LAT0, LNG0 + (i % 100) * 1000 * ONE_METER_LNG, null, i * 1000f);
        }
        assertThat(c.samples()).hasSizeLessThanOrEqualTo(GpsSampleCollector.MAX_POINTS);
    }

    @Test
    @DisplayName("sem nenhum ponto válido, lista vazia")
    void emptyWhenNoGps() {
        GpsSampleCollector c = new GpsSampleCollector();
        c.offer(0, null, null, 150, 0f);
        assertThat(c.samples()).isEmpty();
    }

    @Test
    @DisplayName("haversine: 1° de latitude ≈ 111 km")
    void haversineSanity() {
        assertThat(GpsSampleCollector.haversineM(0, 0, 1, 0)).isCloseTo(111_195, within(200.0));
    }
}
