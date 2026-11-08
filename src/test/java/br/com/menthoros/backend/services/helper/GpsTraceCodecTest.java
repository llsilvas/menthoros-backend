package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.fit.FitGpsSample;
import br.com.menthoros.backend.dto.fit.GpsTracePayload;
import br.com.menthoros.backend.services.impl.FitParseServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

@DisplayName("GpsTraceCodec")
class GpsTraceCodecTest {

    @Test
    @DisplayName("vazio → null (sem trajeto, sem payload)")
    void emptyIsNull() {
        assertThat(GpsTraceCodec.encode(List.of())).isNull();
    }

    @Test
    @DisplayName("polyline bate com o exemplo de referência do Google")
    void matchesGoogleReferenceExample() {
        // Exemplo da documentação do Encoded Polyline Algorithm Format.
        List<FitGpsSample> pts = List.of(
                new FitGpsSample(0, 38.5, -120.2, null),
                new FitGpsSample(1, 40.7, -120.95, null),
                new FitGpsSample(2, 43.252, -126.453, null));

        GpsTracePayload p = GpsTraceCodec.encode(pts);

        assertThat(p.polyline()).isEqualTo("_p~iF~ps|U_ulLnnqC_mqNvxq`@");
        assertThat(p.v()).isEqualTo(1);
        assertThat(p.n()).isEqualTo(3);
        assertThat(p.tSeg()).containsExactly(0, 1, 2);
    }

    @Test
    @DisplayName("ida e volta preserva coordenadas (≤ 1e-5), tempo relativo e FC com nulos")
    void roundTrip() {
        long base = 1_751_360_400L;
        List<FitGpsSample> pts = List.of(
                new FitGpsSample(base, -23.550001, -46.630002, 142),
                new FitGpsSample(base + 7, -23.550900, -46.629100, null),
                new FitGpsSample(base + 19, -23.552345, -46.627654, 158));

        GpsTracePayload p = GpsTraceCodec.encode(pts);
        List<FitGpsSample> back = GpsTraceCodec.decode(p, base);

        assertThat(back).hasSize(3);
        for (int i = 0; i < 3; i++) {
            assertThat(back.get(i).lat()).isCloseTo(pts.get(i).lat(), within(1e-5));
            assertThat(back.get(i).lng()).isCloseTo(pts.get(i).lng(), within(1e-5));
            assertThat(back.get(i).timestampSeg()).isEqualTo(pts.get(i).timestampSeg());
            assertThat(back.get(i).fc()).isEqualTo(pts.get(i).fc());
        }
        assertThat(p.fc()).isEqualTo(Arrays.asList(142, null, 158));
    }

    @Test
    @DisplayName("payload inconsistente (n ≠ pontos) é rejeitado na decodificação")
    void rejectsInconsistentPayload() {
        GpsTracePayload bad = new GpsTracePayload(1, 2, "_p~iF~ps|U", List.of(0, 1), Arrays.asList(null, null));
        assertThatThrownBy(() -> GpsTraceCodec.decode(bad, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("fixture real de 15 km: simplificada cabe em 400 pontos e o JSON fica abaixo de 8 KB")
    void realFixtureFitsBudget() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/fit/corrida-15km-16laps.fit")) {
            var dados = new FitParseServiceImpl().parse(in);
            List<FitGpsSample> simplified = GpsTraceSimplifier.simplify(dados.gpsSamples());
            GpsTracePayload p = GpsTraceCodec.encode(simplified);

            assertThat(simplified).hasSizeBetween(2, GpsTraceSimplifier.MAX_POINTS);
            String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(p);
            assertThat(json.length()).as("bytes aproximados do JSON").isLessThan(8 * 1024);
            // Extremos do simplificado são os extremos do decimado.
            assertThat(simplified.get(0)).isEqualTo(dados.gpsSamples().get(0));
            assertThat(simplified.get(simplified.size() - 1)).isEqualTo(dados.gpsSamples().get(dados.gpsSamples().size() - 1));
        }
    }
}
