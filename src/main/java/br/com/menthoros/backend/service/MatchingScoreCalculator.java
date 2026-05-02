package br.com.menthoros.backend.service;

import br.com.menthoros.backend.dto.MatchingScoreResult;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.entity.TreinoRealizado;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
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
     *
     * @param realizado atividade realizada (do Strava)
     * @param planejado treino planejado
     * @return resultado com scores detalhados (0-1)
     */
    public MatchingScoreResult calculate(
            TreinoRealizado realizado,
            TreinoPlanejado planejado) {

        if (realizado == null || planejado == null) {
            return new MatchingScoreResult(ZERO, ZERO, ZERO, ZERO);
        }

        BigDecimal temporalScore = calculateTemporalScore(realizado, planejado);
        BigDecimal durationScore = calculateDurationScore(realizado, planejado);
        BigDecimal distanceScore = calculateDistanceScore(realizado, planejado);

        BigDecimal overallScore = temporalScore.multiply(TEMPORAL_WEIGHT)
                .add(durationScore.multiply(DURATION_WEIGHT))
                .add(distanceScore.multiply(DISTANCE_WEIGHT))
                .setScale(SCALE, RoundingMode.HALF_UP);

        return new MatchingScoreResult(overallScore, temporalScore, durationScore, distanceScore);
    }

    /**
     * Calcula score de correspondência temporal.
     * Janela ideal: mesma data (score 1.0)
     * Degradação: -0.5 por dia de diferença (máx -1 para >2 dias)
     */
    private BigDecimal calculateTemporalScore(
            TreinoRealizado realizado,
            TreinoPlanejado planejado) {

        try {
            LocalDate realizadoDate = realizado.getDataTreino();
            LocalDate planejadoDate = planejado.getDataTreino();

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
