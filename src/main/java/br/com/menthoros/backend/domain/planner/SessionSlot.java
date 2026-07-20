package br.com.menthoros.backend.domain.planner;

import java.time.DayOfWeek;

/**
 * Posicao de uma sessao dentro do {@link WeekPlanSkeleton} — usada em shadow para o
 * {@code SkeletonComplianceChecker} avaliar posicionamento (ex.: sessao pesada perto de
 * prova). Nao e prescritiva: nao substitui a geracao do LLM nesta change (design.md,
 * "Fora de escopo").
 */
public record SessionSlot(
        DayOfWeek day,
        String sessionType,
        double targetTss,
        String intensityZone,
        boolean chave,
        Integer durationMinutes
) {
}
