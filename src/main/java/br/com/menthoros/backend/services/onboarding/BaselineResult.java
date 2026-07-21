package br.com.menthoros.backend.services.onboarding;

/**
 * Resultado do {@link BaselineCalculator} — CTL/ATL/TSB + origem por
 * componente (design.md Decisao 1, athlete-onboarding-baseline). Tipo puro
 * de calculo, sem acoplamento a persistencia; o mapeamento para
 * {@code AthleteBaselineState} (entidade JPA) fica na camada de servico
 * que persiste (OnboardingService, Secao 5).
 */
public record BaselineResult(
        double ctl,
        OrigemDado ctlOrigem,
        double atl,
        OrigemDado atlOrigem,
        double tsb,
        OrigemDado tsbOrigem
) {
}
