package br.com.menthoros.backend.service;

import br.com.menthoros.backend.dto.MatchingScoreResult;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.entity.TreinoRealizado;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.temporal.ChronoUnit;

/**
 * Serviço para calcular o score de correspondência entre uma atividade realizada
 * e um treino planejado.
 *
 * Algoritmo de scoring ponderado:
 * - Temporal (45%): janela de ±1 dia, degradação linear
 * - Duração (35%): diferença relativa, máx 20% tolerance
 * - Distância (20%): diferença relativa, máx 20% tolerance
 */
@Service
public class MatchingScoreCalculator {

    private static final BigDecimal TEMPORAL_WEIGHT = new BigDecimal("0.45");
    private static final BigDecimal DURATION_WEIGHT = new BigDecimal("0.35");
    private static final BigDecimal DISTANCE_WEIGHT = new BigDecimal("0.20");
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final int SCALE = 4;

    /**
     * Calcula o score de correspondência entre uma atividade realizada e um treino planejado.
     * Normaliza datas usando timezone do atleta (design D13) antes do scoring temporal.
     *
     * @param realizado atividade realizada (do Strava)
     * @param planejado treino planejado
     * @param athlete atleta para resolução de timezone
     * @return resultado com scores detalhados (0-1)
     */
    public MatchingScoreResult calculate(
            TreinoRealizado realizado,
            TreinoPlanejado planejado,
            Atleta athlete) {

        if (realizado == null || planejado == null) {
            return new MatchingScoreResult(ZERO, ZERO, ZERO, ZERO);
        }

        // Resolve timezone do atleta (fallback padrão)
        String timeZoneId = athlete != null && athlete.getTimezone() != null
                ? athlete.getTimezone()
                : "America/Sao_Paulo";

        BigDecimal temporalScore = calculateTemporalScore(realizado, planejado, timeZoneId);
        BigDecimal durationScore = calculateDurationScore(realizado, planejado);
        BigDecimal distanceScore = calculateDistanceScore(realizado, planejado);

        BigDecimal overallScore = temporalScore.multiply(TEMPORAL_WEIGHT)
                .add(durationScore.multiply(DURATION_WEIGHT))
                .add(distanceScore.multiply(DISTANCE_WEIGHT))
                .setScale(SCALE, RoundingMode.HALF_UP);

        return new MatchingScoreResult(overallScore, temporalScore, durationScore, distanceScore);
    }

    /**
     * Calcula score temporal usando timezone do atleta para normalização.
     * Design D13: "Antes do cálculo de score, normalizar start_date e data_treino_planejado
     * para a timezone de referência (timezone do atleta)".
     * Janela ideal: mesma data (score 1.0)
     * Degradação: -0.5 por dia de diferença (máx -1 para >2 dias)
     */
    private BigDecimal calculateTemporalScore(
            TreinoRealizado realizado,
            TreinoPlanejado planejado,
            String timeZoneId) {

        try {
            ZoneId zone = ZoneId.of(timeZoneId);

            // Converter LocalDate para LocalDateTime no timezone do atleta
            LocalDateTime realizadoLDT = realizado.getDataTreino()
                    .atStartOfDay(zone)
                    .toLocalDateTime();
            LocalDateTime planejadoLDT = planejado.getDataTreino()
                    .atStartOfDay(zone)
                    .toLocalDateTime();

            // Comparar datas no mesmo timezone
            LocalDate realizadoDate = realizadoLDT.toLocalDate();
            LocalDate planejadoDate = planejadoLDT.toLocalDate();

            long daysDiff = Math.abs(ChronoUnit.DAYS.between(realizadoDate, planejadoDate));

            if (daysDiff == 0) {
                return ONE;
            } else if (daysDiff == 1) {
                return new BigDecimal("0.75");
            } else if (daysDiff == 2) {
                return new BigDecimal("0.50");
            } else {
                return ZERO;
            }
        } catch (Exception e) {
            return ZERO;
        }
    }

    /**
     * Calcula score de correspondência de duração.
     * Tolerância: ±20% da duração planejada
     * Score degrada linearmente fora dessa faixa.
     */
    private BigDecimal calculateDurationScore(
            TreinoRealizado realizado,
            TreinoPlanejado planejado) {

        if (realizado.getDuracaoMin() == null || planejado.getDuracaoMin() == null) {
            return new BigDecimal("0.5");
        }

        try {
            long planejadoMinutos = planejado.getDuracaoMin().toMinutes();
            long realizadoMinutos = realizado.getDuracaoMin().toMinutes();

            if (planejadoMinutos == 0) {
                return ZERO;
            }

            long diffMinutos = Math.abs(realizadoMinutos - planejadoMinutos);
            BigDecimal relativeError = new BigDecimal(diffMinutos)
                    .divide(new BigDecimal(planejadoMinutos), SCALE, RoundingMode.HALF_UP);

            if (relativeError.compareTo(new BigDecimal("0.20")) <= 0) {
                return ONE;
            } else if (relativeError.compareTo(new BigDecimal("0.50")) >= 0) {
                return ZERO;
            } else {
                return ONE.subtract(
                        relativeError.subtract(new BigDecimal("0.20"))
                                .divide(new BigDecimal("0.30"), SCALE, RoundingMode.HALF_UP)
                );
            }
        } catch (Exception e) {
            return new BigDecimal("0.5");
        }
    }

    /**
     * Calcula score de correspondência de distância.
     * Tolerância: ±20% da distância planejada
     * Score degrada linearmente fora dessa faixa.
     * Se distância não estiver disponível, retorna score neutro.
     */
    private BigDecimal calculateDistanceScore(
            TreinoRealizado realizado,
            TreinoPlanejado planejado) {

        if (realizado.getDistanciaKm() == null || planejado.getDistanciaKm() == null) {
            return new BigDecimal("0.5");
        }

        try {
            BigDecimal planejadoKm = planejado.getDistanciaKm();
            BigDecimal realizadoKm = realizado.getDistanciaKm();

            if (planejadoKm.compareTo(ZERO) == 0) {
                return ZERO;
            }

            BigDecimal diffKm = realizadoKm.subtract(planejadoKm).abs();
            BigDecimal relativeError = diffKm
                    .divide(planejadoKm, SCALE, RoundingMode.HALF_UP);

            if (relativeError.compareTo(new BigDecimal("0.20")) <= 0) {
                return ONE;
            } else if (relativeError.compareTo(new BigDecimal("0.50")) >= 0) {
                return ZERO;
            } else {
                return ONE.subtract(
                        relativeError.subtract(new BigDecimal("0.20"))
                                .divide(new BigDecimal("0.30"), SCALE, RoundingMode.HALF_UP)
                );
            }
        } catch (Exception e) {
            return new BigDecimal("0.5");
        }
    }
}
