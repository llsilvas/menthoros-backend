package br.com.menthoros.backend.services.onboarding;

import br.com.menthoros.backend.enums.DispositivoMarca;

import java.math.BigDecimal;
import java.util.List;

/**
 * Entrada do {@link ConfidenceScorer} (design.md Decisao 3, athlete-onboarding-baseline).
 * Tipo puro — nao recebe entidades JPA (Skills Architecture Standards do
 * CLAUDE.md do backend); o service que orquestra (OnboardingService,
 * Secao 5) monta este record a partir de {@code Atleta} e do historico
 * normalizado.
 *
 * <p>{@code dispositivoMarca} (retrofit 10.6, sessao de grilling 2026-07-21): usado como
 * PRIOR do criterio "Fonte confiavel" quando {@code historicoDeduplicado} ainda esta vazio
 * (atleta recem-onboarded, sem atividade real) — nulo quando nao declarado ou quando ja
 * existe historico (o dado real sempre substitui o prior).
 */
public record ConfidenceScorerInput(
        List<NormalizedActivity> historicoDeduplicado,
        boolean onboardingCompleto,
        Integer fcMaxima,
        Integer fcRepouso,
        BigDecimal paceLimiar,
        boolean temProvaRecente,
        boolean preenchidoPorCoach,
        DispositivoMarca dispositivoMarca
) {
}
