package br.com.menthoros.backend.dto;

import br.com.menthoros.backend.enums.ReconciliationStatus;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import java.math.BigDecimal;
import java.util.List;

/**
 * Resultado da decisão de reconciliação para uma atividade realizada.
 */
public class MatchingDecision {

    private final ReconciliationStatus status;
    private final TreinoPlanejado selectedPlanned;
    private final List<MatchingCandidate> rankedCandidates;
    private final String reasonCode;
    private final String reasonText;

    public MatchingDecision(
            ReconciliationStatus status,
            TreinoPlanejado selectedPlanned,
            List<MatchingCandidate> rankedCandidates,
            String reasonCode,
            String reasonText) {
        this.status = status;
        this.selectedPlanned = selectedPlanned;
        this.rankedCandidates = rankedCandidates;
        this.reasonCode = reasonCode;
        this.reasonText = reasonText;
    }

    public ReconciliationStatus getStatus() {
        return status;
    }

    public TreinoPlanejado getSelectedPlanned() {
        return selectedPlanned;
    }

    public List<MatchingCandidate> getRankedCandidates() {
        return rankedCandidates;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public String getReasonText() {
        return reasonText;
    }

    public BigDecimal getSelectedScore() {
        if (selectedPlanned != null && !rankedCandidates.isEmpty()) {
            return rankedCandidates.get(0).getOverallScore();
        }
        return null;
    }
}
