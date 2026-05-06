package br.com.menthoros.backend.dto;

import br.com.menthoros.backend.entity.TreinoPlanejado;
import java.math.BigDecimal;

/**
 * Representa um candidato de treino planejado com seu score de correspondência.
 */
public class MatchingCandidate implements Comparable<MatchingCandidate> {

    private final TreinoPlanejado planned;
    private final MatchingScoreResult scoreResult;
    private final int rank;

    public MatchingCandidate(
            TreinoPlanejado planned,
            MatchingScoreResult scoreResult,
            int rank) {
        this.planned = planned;
        this.scoreResult = scoreResult;
        this.rank = rank;
    }

    public TreinoPlanejado getPlanned() {
        return planned;
    }

    public MatchingScoreResult getScoreResult() {
        return scoreResult;
    }

    public BigDecimal getOverallScore() {
        return scoreResult.getOverallScore();
    }

    public int getRank() {
        return rank;
    }

    @Override
    public int compareTo(MatchingCandidate other) {
        return other.getOverallScore().compareTo(this.getOverallScore());
    }
}
