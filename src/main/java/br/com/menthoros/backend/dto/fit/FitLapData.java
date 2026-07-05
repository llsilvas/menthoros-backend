package br.com.menthoros.backend.dto.fit;

import java.time.Duration;

/**
 * Dados de um lap (split) extraídos de um arquivo .fit — POJO interno, não é DTO de API.
 */
public record FitLapData(
        int ordem,
        Duration duracao,
        Double distanciaKm,
        Integer fcMedia,
        Integer fcMax
) {}
