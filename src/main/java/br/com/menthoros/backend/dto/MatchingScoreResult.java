package br.com.menthoros.backend.dto;

import java.math.BigDecimal;

/**
 * Resultado do cálculo de score de correspondência entre uma atividade realizada
 * e um treino planejado.
 */
public class MatchingScoreResult {

    private final BigDecimal overallScore;
    private final BigDecimal temporalScore;
    private final BigDecimal durationScore;
    private final BigDecimal distanceScore;

    public MatchingScoreResult(
            BigDecimal overallScore,
            BigDecimal temporalScore,
            BigDecimal durationScore,
            BigDecimal distanceScore) {
        this.overallScore = overallScore;
        this.temporalScore = temporalScore;
        this.durationScore = durationScore;
        this.distanceScore = distanceScore;
    }

    public BigDecimal getOverallScore() {
        return overallScore;
    }

    public BigDecimal getTemporalScore() {
        return temporalScore;
    }

    public BigDecimal getDurationScore() {
        return durationScore;
    }

    public BigDecimal getDistanceScore() {
        return distanceScore;
    }
}
