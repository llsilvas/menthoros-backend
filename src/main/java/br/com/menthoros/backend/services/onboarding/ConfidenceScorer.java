package br.com.menthoros.backend.services.onboarding;

/**
 * Calcula o score de confianca do onboarding (design.md Decisao 3,
 * athlete-onboarding-baseline) — 8 criterios ponderados, soma 0-100,
 * classificado em tier A (&gt;=75) / B (45-74) / C (&lt;45), com bonus
 * coach-como-proxy (sobe um tier, nunca desce).
 */
public interface ConfidenceScorer {

    /**
     * Idempotente: SIM — a mesma entrada sempre produz o mesmo resultado.
     * Efeitos colaterais: NENHUM (calculo puro).
     * Tenant-aware: NAO aplicavel (nao acessa persistencia).
     */
    ConfidenceScoreResult calcular(ConfidenceScorerInput input);
}
