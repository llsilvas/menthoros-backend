package br.com.menthoros.backend.service;

import br.com.menthoros.backend.dto.MatchingCandidate;
import br.com.menthoros.backend.dto.MatchingDecision;
import br.com.menthoros.backend.entity.TreinoRealizado;

import java.util.List;

/**
 * Applies decision rules to determine reconciliation outcome.
 *
 * Rules:
 * - score ≥ 0.80: auto-match (VINCULADO_AUTOMATICO)
 * - 0.50-0.79: ambiguous (AMBIGUO, requires manual review)
 * - < 0.50: orphaned (NAO_PLANEJADO, no match found)
 * - Tie-breaking: if top 1 score - top 2 score < 0.10, mark as ambiguous
 */
public interface MatchingDecisionEngine {
    MatchingDecision decide(TreinoRealizado realizado, List<MatchingCandidate> candidates);
}
