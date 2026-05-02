package br.com.menthoros.backend.service;

import br.com.menthoros.backend.dto.MatchingCandidate;
import br.com.menthoros.backend.dto.MatchingDecision;
import br.com.menthoros.backend.dto.MatchingScoreResult;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.ReconciliationStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Serviço de decisão de reconciliação usando regras baseadas em limiares e tie-breaking.
 *
 * Regras de decisão:
 * - score >= 0.80: VINCULADO_AUTOMATICO
 * - 0.50 <= score < 0.80: AMBIGUO (não auto-vincula)
 * - score < 0.50: NAO_PLANEJADO
 * - tie-breaking: se top1 - top2 < 0.10 => AMBIGUO
 */
@Service
public class MatchingDecisionEngine {

    private static final BigDecimal AUTOMATIC_THRESHOLD = new BigDecimal("0.80");
    private static final BigDecimal AMBIGUOUS_THRESHOLD = new BigDecimal("0.50");
    private static final BigDecimal TIE_THRESHOLD = new BigDecimal("0.10");

    /**
     * Toma decisão de reconciliação para uma atividade realizada contra uma lista de candidatos.
     *
     * @param realizado atividade realizada
     * @param candidates lista de candidatos com scores
     * @return decisão com status, candidato selecionado (se houver) e texto de razão
     */
    public MatchingDecision decide(
            TreinoRealizado realizado,
            List<MatchingCandidate> candidates) {

        if (candidates == null || candidates.isEmpty()) {
            return new MatchingDecision(
                    ReconciliationStatus.NAO_PLANEJADO,
                    null,
                    Collections.emptyList(),
                    "NO_CANDIDATES",
                    "Nenhum candidato de treino planejado encontrado"
            );
        }

        List<MatchingCandidate> sorted = new ArrayList<>(candidates);
        Collections.sort(sorted);

        MatchingCandidate top = sorted.get(0);
        BigDecimal topScore = top.getOverallScore();

        if (topScore.compareTo(AMBIGUOUS_THRESHOLD) < 0) {
            return new MatchingDecision(
                    ReconciliationStatus.NAO_PLANEJADO,
                    null,
                    sorted,
                    "SCORE_TOO_LOW",
                    String.format("Score máximo %.2f está abaixo do limiar (%.2f)", topScore, AMBIGUOUS_THRESHOLD)
            );
        }

        if (isTie(sorted)) {
            return new MatchingDecision(
                    ReconciliationStatus.AMBIGUO,
                    null,
                    sorted,
                    "TIE_DETECTED",
                    String.format("Empate: top1=%.2f, top2=%.2f (delta < %.2f)",
                            sorted.get(0).getOverallScore(),
                            sorted.get(1).getOverallScore(),
                            TIE_THRESHOLD)
            );
        }

        if (topScore.compareTo(AUTOMATIC_THRESHOLD) >= 0) {
            return new MatchingDecision(
                    ReconciliationStatus.VINCULADO_AUTOMATICO,
                    top.getPlanned(),
                    sorted,
                    "AUTO_MATCHED",
                    String.format("Score %.2f >= limiar automático (%.2f)", topScore, AUTOMATIC_THRESHOLD)
            );
        }

        return new MatchingDecision(
                ReconciliationStatus.AMBIGUO,
                null,
                sorted,
                "LOW_CONFIDENCE",
                String.format("Score %.2f está na faixa ambígua (%.2f-%.2f)",
                        topScore, AMBIGUOUS_THRESHOLD, AUTOMATIC_THRESHOLD)
        );
    }

    /**
     * Verifica se o melhor e segundo melhor candidatos estão em empate.
     * Empate: diferença < 0.10
     */
    private boolean isTie(List<MatchingCandidate> sorted) {
        if (sorted.size() < 2) {
            return false;
        }

        BigDecimal top1 = sorted.get(0).getOverallScore();
        BigDecimal top2 = sorted.get(1).getOverallScore();

        BigDecimal delta = top1.subtract(top2).abs();
        return delta.compareTo(TIE_THRESHOLD) < 0;
    }
}
