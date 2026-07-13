package br.com.menthoros.backend.dto.fit;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Dados de um lap (split) extraídos de um arquivo .fit — POJO interno, não é DTO de API.
 *
 * @param cadenciaMediaPpm cadência em passos por minuto de DUAS pernas — a FIT grava passos de
 *                         uma perna; a conversão (incluindo a fração de {@code avgFractionalCadence})
 *                         acontece no parser, este record já carrega o valor final.
 * @param tempoMovimento   tempo em movimento do lap ({@code getTotalTimerTime()}) — nullable,
 *                         separado de {@code duracao} ({@code totalElapsedTime}, sempre presente).
 *                         Usado pelo persister para corrigir pace/velocidade em laps com pausa
 *                         (design D6 de fit-running-dynamics-ingestion), não substitui {@code duracao}.
 * @param gctMedioMs           tempo médio de contato com o solo, em ms ({@code getAvgStanceTime()}).
 * @param gctEquilibrioPct     % de GCT do pé esquerdo ({@code getAvgStanceTimeBalance()}).
 * @param passadaMediaM        comprimento médio da passada, em metros (FIT entrega em mm).
 * @param oscilacaoVerticalCm  oscilação vertical média, em cm (FIT entrega em mm).
 * @param proporcaoVerticalPct proporção vertical média, em % ({@code getAvgVerticalRatio()}).
 * @param temperaturaMediaC    temperatura média, em °C ({@code getAvgTemperature()}).
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
        Integer cadenciaMediaPpm,
        Duration tempoMovimento,
        Integer gctMedioMs,
        BigDecimal gctEquilibrioPct,
        BigDecimal passadaMediaM,
        BigDecimal oscilacaoVerticalCm,
        BigDecimal proporcaoVerticalPct,
        BigDecimal temperaturaMediaC
) {}
