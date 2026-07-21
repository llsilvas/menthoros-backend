package br.com.menthoros.backend.services.onboarding;

import java.math.BigDecimal;
import java.util.List;

/**
 * Entrada do {@link ConfidenceScorer} (design.md Decisao 3, athlete-onboarding-baseline).
 * Tipo puro — nao recebe entidades JPA (Skills Architecture Standards do
 * CLAUDE.md do backend); o service que orquestra (OnboardingService,
 * Secao 5) monta este record a partir de {@code Atleta} e do historico
 * normalizado.
 */
public record ConfidenceScorerInput(
        List<NormalizedActivity> historicoDeduplicado,
        boolean onboardingCompleto,
        Integer fcMaxima,
        Integer fcRepouso,
        BigDecimal paceLimiar,
        boolean temProvaRecente,
        boolean preenchidoPorCoach
) {
}
