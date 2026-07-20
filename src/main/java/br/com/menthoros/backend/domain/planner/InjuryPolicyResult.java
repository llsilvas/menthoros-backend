package br.com.menthoros.backend.domain.planner;

import java.util.Optional;

/**
 * Saida do {@code InjuryPolicyResolver} (design.md Decisao 14) — override de fase por sinais
 * de lesao do atleta (independente do calendario de provas) mais motivo textual para auditoria
 * e para {@code requiresCoachReview}.
 */
public record InjuryPolicyResult(
        Optional<TrainingPhase> phaseOverride,
        boolean requiresCoachReview,
        String reason
) {
    private static final InjuryPolicyResult FLUXO_NORMAL = new InjuryPolicyResult(Optional.empty(), false, null);

    public static InjuryPolicyResult fluxoNormal() {
        return FLUXO_NORMAL;
    }
}
