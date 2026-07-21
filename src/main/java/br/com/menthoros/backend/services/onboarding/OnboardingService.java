package br.com.menthoros.backend.services.onboarding;

import br.com.menthoros.backend.domain.planner.OnboardingContext;

import java.util.UUID;

/**
 * Orquestra o fluxo completo do onboarding (design.md, athlete-onboarding-baseline):
 * {@link ActivityNormalizer} -&gt; {@link ActivityDedupService} -&gt;
 * {@link BaselineCalculator} -&gt; {@link ConfidenceScorer} -&gt;
 * {@link PlanningPolicyResolver} -&gt; {@code OnboardingContext} (tipo reservado por
 * {@code deterministic-planner-engine}). Persiste o {@code AthleteBaselineSnapshot}
 * calculado.
 */
public interface OnboardingService {

    /**
     * Idempotente: NAO — persiste/atualiza {@code AthleteBaselineSnapshot} a cada chamada
     * (upsert por atleta+tenant; chamadas repetidas recalculam e sobrescrevem, sem duplicar).
     * Efeitos colaterais: persiste {@code AthleteBaselineSnapshot}; le
     * {@code TreinoRealizado}/{@code PerfilOnboardingAtleta}/{@code Atleta}.
     * Tenant-aware: SIM — {@code tenantId} explicito.
     *
     * @throws br.com.menthoros.backend.exception.DomainNotFoundException se o atleta nao existir no tenant
     */
    OnboardingContext montarContexto(UUID atletaId, UUID tenantId);
}
