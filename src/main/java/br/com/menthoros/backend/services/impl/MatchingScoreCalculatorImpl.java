package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.MatchingScoreResult;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.services.MatchingScoreCalculator;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;

@Service
public class MatchingScoreCalculatorImpl implements MatchingScoreCalculator {

    private static final BigDecimal TEMPORAL_WEIGHT = new BigDecimal("0.45");
    private static final BigDecimal DURATION_WEIGHT = new BigDecimal("0.35");
    private static final BigDecimal DISTANCE_WEIGHT = new BigDecimal("0.20");

    @Override
    public MatchingScoreResult calculate(TreinoRealizado realizado, TreinoPlanejado planejado, Atleta athlete) {
        BigDecimal temporalScore = calculateTemporalScore(realizado.getDataTreino(), planejado.getDataTreino());
        BigDecimal durationScore = calculateDurationScore(realizado.getDuracaoMin(), planejado.getDuracaoMin());
        BigDecimal distanceScore = calculateDistanceScore(realizado.getDistanciaKm(), planejado.getDistanciaKm());

        BigDecimal overallScore = temporalScore.multiply(TEMPORAL_WEIGHT)
                .add(durationScore.multiply(DURATION_WEIGHT))
                .add(distanceScore.multiply(DISTANCE_WEIGHT))
                .setScale(2, RoundingMode.HALF_UP);

        return new MatchingScoreResult(overallScore, temporalScore, durationScore, distanceScore);
    }

    private BigDecimal calculateTemporalScore(LocalDate realizadoDate, LocalDate planejadoDate) {
        if (realizadoDate == null || planejadoDate == null) {
            return BigDecimal.ONE;
        }

        long daysDiff = Math.abs(java.time.temporal.ChronoUnit.DAYS.between(realizadoDate, planejadoDate));

        if (daysDiff == 0) {
            return BigDecimal.ONE;
        } else if (daysDiff <= 1) {
            return new BigDecimal("0.9");
        } else if (daysDiff <= 2) {
            return new BigDecimal("0.7");
        } else if (daysDiff <= 7) {
            return new BigDecimal("0.5");
        } else {
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal calculateDurationScore(Duration realizadoDuration, Duration planejadoDuration) {
        if (realizadoDuration == null || planejadoDuration == null) {
            return BigDecimal.ONE;
        }

        long realizadoMinutes = realizadoDuration.toMinutes();
        long planejadoMinutes = planejadoDuration.toMinutes();

        if (planejadoMinutes == 0) {
            return BigDecimal.ZERO;
        }

        double percentDiff = Math.abs((double) (realizadoMinutes - planejadoMinutes)) / planejadoMinutes;

        if (percentDiff <= 0.05) {
            return BigDecimal.ONE;
        } else if (percentDiff <= 0.10) {
            return new BigDecimal("0.9");
        } else if (percentDiff <= 0.20) {
            return new BigDecimal("0.7");
        } else if (percentDiff <= 0.35) {
            return new BigDecimal("0.5");
        } else {
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal calculateDistanceScore(java.math.BigDecimal realizadoDistance, java.math.BigDecimal planejadoDistance) {
        if (realizadoDistance == null || planejadoDistance == null) {
            return BigDecimal.ONE;
        }

        if (planejadoDistance.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal diff = realizadoDistance.subtract(planejadoDistance).abs();
        BigDecimal percentDiff = diff.divide(planejadoDistance, 4, RoundingMode.HALF_UP);

        if (percentDiff.compareTo(new BigDecimal("0.05")) <= 0) {
            return BigDecimal.ONE;
        } else if (percentDiff.compareTo(new BigDecimal("0.10")) <= 0) {
            return new BigDecimal("0.9");
        } else if (percentDiff.compareTo(new BigDecimal("0.20")) <= 0) {
            return new BigDecimal("0.7");
        } else if (percentDiff.compareTo(new BigDecimal("0.35")) <= 0) {
            return new BigDecimal("0.5");
        } else {
            return BigDecimal.ZERO;
        }
    }
}
