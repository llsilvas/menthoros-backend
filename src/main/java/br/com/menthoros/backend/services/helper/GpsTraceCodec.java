package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.fit.FitGpsSample;
import br.com.menthoros.backend.dto.fit.GpsTracePayload;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Codifica o trajeto simplificado no formato persistido (add-activity-gps-trace, D2) e decodifica
 * de volta — a decodificação existe para o teste de ida e volta e para quem precisar dos pontos.
 *
 * <p>Polyline no algoritmo do Google Maps (precisão 5): deltas em 1e-5 grau, zig-zag, base 32
 * com continuação. ~5 bytes por ponto. Função pura.
 */
public final class GpsTraceCodec {

    private static final double PRECISION = 1e5;
    // O payload é só primitivos e listas: um mapper padrão basta e evita injetar o bean no persister.
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private GpsTraceCodec() {}

    /**
     * Decima → simplifica → codifica → JSON para {@code tb_treino_realizado.gps_trace}.
     * {@code null} quando não há trajeto (menos de 2 pontos).
     */
    public static @Nullable String toJson(List<FitGpsSample> decimatedSamples) {
        GpsTracePayload payload = encode(GpsTraceSimplifier.simplify(decimatedSamples));
        if (payload == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar gps_trace", e);
        }
    }

    public static GpsTracePayload fromJson(String json) {
        try {
            return MAPPER.readValue(json, GpsTracePayload.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("gps_trace inválido", e);
        }
    }

    /** Lista vazia → {@code null}: sem trajeto não há payload a persistir. */
    public static @Nullable GpsTracePayload encode(List<FitGpsSample> points) {
        if (points.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder(points.size() * 6);
        long prevLat = 0, prevLng = 0;
        long t0 = points.get(0).timestampSeg();
        List<Integer> tSeg = new ArrayList<>(points.size());
        List<Integer> fc = new ArrayList<>(points.size());
        for (FitGpsSample p : points) {
            long lat = Math.round(p.lat() * PRECISION);
            long lng = Math.round(p.lng() * PRECISION);
            encodeValue(lat - prevLat, sb);
            encodeValue(lng - prevLng, sb);
            prevLat = lat;
            prevLng = lng;
            tSeg.add((int) (p.timestampSeg() - t0));
            fc.add(p.fc());
        }
        return new GpsTracePayload(GpsTracePayload.CURRENT_VERSION, points.size(), sb.toString(),
                List.copyOf(tSeg), java.util.Collections.unmodifiableList(fc));
    }

    /** Reconstrói os pontos; {@code baseTimestampSeg} é o epoch do primeiro ponto (0 se não importa). */
    public static List<FitGpsSample> decode(GpsTracePayload payload, long baseTimestampSeg) {
        List<double[]> coords = decodePolyline(payload.polyline());
        if (coords.size() != payload.n() || payload.tSeg().size() != payload.n() || payload.fc().size() != payload.n()) {
            throw new IllegalArgumentException("gps_trace inconsistente: n=" + payload.n()
                    + ", polyline=" + coords.size() + ", tSeg=" + payload.tSeg().size() + ", fc=" + payload.fc().size());
        }
        List<FitGpsSample> out = new ArrayList<>(payload.n());
        for (int i = 0; i < payload.n(); i++) {
            out.add(new FitGpsSample(baseTimestampSeg + payload.tSeg().get(i), coords.get(i)[0], coords.get(i)[1],
                    payload.fc().get(i)));
        }
        return out;
    }

    private static void encodeValue(long value, StringBuilder sb) {
        long v = value < 0 ? ~(value << 1) : value << 1;
        while (v >= 0x20) {
            sb.append((char) ((0x20 | (v & 0x1f)) + 63));
            v >>= 5;
        }
        sb.append((char) (v + 63));
    }

    static List<double[]> decodePolyline(String s) {
        List<double[]> out = new ArrayList<>();
        int i = 0;
        long lat = 0, lng = 0;
        while (i < s.length()) {
            long[] r = decodeValue(s, i);
            lat += r[0];
            i = (int) r[1];
            r = decodeValue(s, i);
            lng += r[0];
            i = (int) r[1];
            out.add(new double[]{lat / PRECISION, lng / PRECISION});
        }
        return out;
    }

    private static long[] decodeValue(String s, int i) {
        long result = 0;
        int shift = 0;
        int b;
        do {
            if (i >= s.length()) {
                throw new IllegalArgumentException("polyline truncada");
            }
            b = s.charAt(i++) - 63;
            result |= (long) (b & 0x1f) << shift;
            shift += 5;
        } while (b >= 0x20);
        long value = (result & 1) != 0 ? ~(result >> 1) : result >> 1;
        return new long[]{value, i};
    }
}
