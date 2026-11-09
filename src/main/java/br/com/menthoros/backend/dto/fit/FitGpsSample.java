package br.com.menthoros.backend.dto.fit;

import org.jspecify.annotations.Nullable;

/**
 * Ponto GPS de uma mensagem {@code record} do .fit, já convertido de semicírculo para grau e já
 * decimado pelo {@link br.com.menthoros.backend.services.helper.GpsSampleCollector}.
 *
 * @param timestampSeg epoch em segundos do record (mesma base de {@link FitAmostraFc})
 * @param lat          latitude em graus decimais
 * @param lng          longitude em graus decimais
 * @param fc           FC do record, nula quando o sensor não registrou
 */
public record FitGpsSample(long timestampSeg, double lat, double lng, @Nullable Integer fc) {
}
