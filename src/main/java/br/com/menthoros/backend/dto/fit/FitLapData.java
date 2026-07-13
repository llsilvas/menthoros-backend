package br.com.menthoros.backend.dto.fit;

import java.time.Duration;

/**
 * Dados de um lap (split) extraídos de um arquivo .fit — POJO interno, não é DTO de API.
 *
 * @param cadenciaMediaPpm cadência em passos por minuto de DUAS pernas — a FIT grava passos de
 *                         uma perna; a conversão (incluindo a fração de {@code avgFractionalCadence})
 *                         acontece no parser, este record já carrega o valor final.
 */
public record FitLapData(
        int ordem,
        Duration duracao,
        Double distanciaKm,
        Integer fcMedia,
        Integer fcMax,
        Integer subidaMetros,
        Integer descidaMetros,
        Integer potenciaMediaWatts,
        Integer cadenciaMediaPpm
) {}
