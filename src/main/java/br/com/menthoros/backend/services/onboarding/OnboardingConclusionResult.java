package br.com.menthoros.backend.services.onboarding;

import br.com.menthoros.backend.domain.planner.OnboardingContext;
import br.com.menthoros.backend.entity.PerfilOnboardingAtleta;
import br.com.menthoros.backend.entity.Prova;

/**
 * Resultado da conclusao do onboarding (task 6.0.3, athlete-onboarding-baseline) — combina o
 * perfil migrado, a {@code Prova} alvo criada/atualizada a partir do {@code dataProva} (CA13) e
 * o {@code OnboardingContext} recem-calculado (baseline + score + tier).
 */
public record OnboardingConclusionResult(
        PerfilOnboardingAtleta perfil,
        Prova provaAlvo,
        OnboardingContext context,
        ConfidenceTier confidenceTier
) {
}
