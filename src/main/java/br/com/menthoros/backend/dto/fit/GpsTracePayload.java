package br.com.menthoros.backend.dto.fit;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Trajeto persistido em {@code tb_treino_realizado.gps_trace} (JSONB) e devolvido pela API
 * (add-activity-gps-trace, D2). Arrays paralelos ao trajeto simplificado.
 *
 * @param v        versão do formato (1)
 * @param n        número de pontos
 * @param polyline Google encoded polyline, precisão 5 (~1,1 m)
 * @param tSeg     segundos desde o primeiro ponto, um por ponto
 * @param fc       FC por ponto, nula onde o sensor não registrou
 */
public record GpsTracePayload(
        int v,
        int n,
        String polyline,
        List<Integer> tSeg,
        List<@Nullable Integer> fc
) {
    public static final int CURRENT_VERSION = 1;
}
