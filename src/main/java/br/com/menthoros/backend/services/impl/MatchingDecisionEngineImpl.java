package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.MatchingCandidate;
import br.com.menthoros.backend.dto.MatchingDecision;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.ReconciliationStatus;
import br.com.menthoros.backend.services.MatchingDecisionEngine;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

@Service
public class MatchingDecisionEngineImpl implements MatchingDecisionEngine {

    private static final BigDecimal AUTO_MATCH_THRESHOLD = new BigDecimal("0.80");
    private static final BigDecimal AMBIGUOUS_LOWER = new BigDecimal("0.50");
    private static final BigDecimal TIE_BREAK_THRESHOLD = new BigDecimal("0.10");

    @Override
    public MatchingDecision decide(TreinoRealizado realizado, List<MatchingCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return new MatchingDecision(
                    ReconciliationStatus.NAO_PLANEJADO,
                    null,
                    Collections.emptyList(),
                    "NO_CANDIDATES",
                    "Nenhum candidato de treino planejado encontrado na janela de tempo"
            );
        }

        List<MatchingCandidate> sorted = candidates.stream()
                .sorted(MatchingCandidate::compareTo)
                .toList();

        MatchingCandidate topCandidate = sorted.get(0);
        BigDecimal topScore = topCandidate.getOverallScore();

        if (topScore == null || topScore.compareTo(BigDecimal.ZERO) <= 0) {
            return new MatchingDecision(
                    ReconciliationStatus.NAO_PLANEJADO,
                    null,
                    sorted,
                    "NO_MATCH",
                    "Melhor candidato possui score ≤ 0"
            );
        }

        if (topScore.compareTo(AUTO_MATCH_THRESHOLD) >= 0) {
            if (isTieBreaker(sorted)) {
                return new MatchingDecision(
                        ReconciliationStatus.AMBIGUO,
                        topCandidate.getPlanned(),
                        sorted,
                        "TIE_BREAK",
                        "Diferença entre top candidatos < 10% — requer revisão manual"
                );
            }
            return new MatchingDecision(
                    ReconciliationStatus.VINCULADO_AUTOMATICO,
                    topCandidate.getPlanned(),
                    sorted,
                    "AUTO_MATCH",
                    "Score ≥ 0.80 — vínculo automático com " + topCandidate.getPlanned().getId()
            );
        } else if (topScore.compareTo(AMBIGUOUS_LOWER) >= 0) {
            return new MatchingDecision(
                    ReconciliationStatus.AMBIGUO,
                    topCandidate.getPlanned(),
                    sorted,
                    "AMBIGUOUS",
                    "Score entre 0.50-0.79 — requer revisão manual"
            );
        } else {
            return new MatchingDecision(
                    ReconciliationStatus.NAO_PLANEJADO,
                    null,
                    sorted,
                    "ORPHANED",
                    "Score < 0.50 — nenhuma correspondência válida encontrada"
            );
        }
    }

    private boolean isTieBreaker(List<MatchingCandidate> sorted) {
        if (sorted.size() < 2) {
            return false;
        }

        BigDecimal topScore = sorted.get(0).getOverallScore();
        BigDecimal secondScore = sorted.get(1).getOverallScore();

        if (topScore == null || secondScore == null) {
            return false;
        }

        BigDecimal diff = topScore.subtract(secondScore).abs();
        return diff.compareTo(TIE_BREAK_THRESHOLD) < 0;
    }
}
