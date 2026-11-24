package br.com.menthoros.backend.domain.planner;

import org.jspecify.annotations.Nullable;

/**
 * Alvo de carga da semana resolvido pelo {@code LoadTargetResolver} — combina fase, taper,
 * risco e {@code DecisaoProgressao} (design.md Decisao 6).
 *
 * <p>{@code seasonTargetTss} e {@code seasonCutReason} (add-season-plan-load-enforcement): só no caminho
 * da temporada — o {@code targetTss} da semana usado como base e a proteção que cortou o alvo abaixo dele
 * (nula sem corte). O desvio é {@code targetTss − seasonTargetTss}.
 */
public record WeeklyLoadTarget(
        double targetTss,
        double minTss,
        double maxTss,
        String rationale,
        @Nullable Double seasonTargetTss,
        @Nullable SeasonCutReason seasonCutReason
) {

    /** Alvo fora do caminho da temporada. */
    public WeeklyLoadTarget(double targetTss, double minTss, double maxTss, String rationale) {
        this(targetTss, minTss, maxTss, rationale, null, null);
    }
}
