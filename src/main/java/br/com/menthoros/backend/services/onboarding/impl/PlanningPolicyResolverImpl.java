package br.com.menthoros.backend.services.onboarding.impl;

import br.com.menthoros.backend.domain.planner.PlanningPolicy;
import br.com.menthoros.backend.domain.planner.ReviewMode;
import br.com.menthoros.backend.services.onboarding.ConfidenceTier;
import br.com.menthoros.backend.services.onboarding.PlanningPolicyResolver;
import org.springframework.stereotype.Component;

/**
 * Implementacao do resolver de PlanningPolicy (design.md Decisao 4,
 * athlete-onboarding-baseline) — tabela fixa de 3 faixas.
 */
@Component
public class PlanningPolicyResolverImpl implements PlanningPolicyResolver {

    private static final double PROGRESSAO_NORMAL = 1.0;
    private static final double PROGRESSAO_REDUZIDA = 0.5; // fracao do normal, design.md Decisao 4
    private static final double PROGRESSAO_ZERO = 0.0;

    @Override
    public PlanningPolicy resolver(ConfidenceTier tier) {
        if (tier == null) {
            throw new IllegalArgumentException("tier nao pode ser nulo");
        }
        return switch (tier) {
            case A -> new PlanningPolicy(ReviewMode.EXCEPTION_ONLY, PROGRESSAO_NORMAL, true);
            case B -> new PlanningPolicy(ReviewMode.MANDATORY_NON_BLOCKING, PROGRESSAO_REDUZIDA, true);
            case C -> new PlanningPolicy(ReviewMode.MANDATORY_BLOCKING, PROGRESSAO_ZERO, true);
        };
    }
}
