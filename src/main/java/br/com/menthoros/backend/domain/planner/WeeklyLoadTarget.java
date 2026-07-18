package br.com.menthoros.backend.domain.planner;

/**
 * Alvo de carga da semana resolvido pelo {@code LoadTargetResolver} — combina fase, taper,
 * risco e {@code DecisaoProgressao} (design.md Decisao 6).
 */
public record WeeklyLoadTarget(
        double targetTss,
        double minTss,
        double maxTss,
        String rationale
) {
}
