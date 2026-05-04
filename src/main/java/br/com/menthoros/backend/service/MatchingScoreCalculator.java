package br.com.menthoros.backend.service;

import br.com.menthoros.backend.dto.MatchingScoreResult;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.entity.TreinoRealizado;

/**
 * Calculates matching score (0-1) between a realized workout (from Strava) and a planned workout.
 *
 * Scoring algorithm weights:
 * - Temporal distance: 45% (same day or adjacent days are best)
 * - Duration difference: 35% (duration close to planned)
 * - Distance difference: 20% (if applicable)
 */
public interface MatchingScoreCalculator {
    MatchingScoreResult calculate(TreinoRealizado realizado, TreinoPlanejado planejado, Atleta athlete);
}
