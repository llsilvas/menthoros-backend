package br.com.menthoros.backend.services.onboarding;

import br.com.menthoros.backend.domain.planner.PlanningPolicy;

/**
 * Deriva {@code PlanningPolicy} (tipo reservado por {@code deterministic-planner-engine})
 * da faixa de confianca (design.md Decisao 4, athlete-onboarding-baseline).
 */
public interface PlanningPolicyResolver {

    /**
     * Idempotente: SIM. Efeitos colaterais: NENHUM. Tenant-aware: NAO aplicavel.
     */
    PlanningPolicy resolver(ConfidenceTier tier);
}
