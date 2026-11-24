package br.com.menthoros.backend.domain.planner;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Semana da temporada {@code ACTIVE} que coincide com a semana do plano em geração
 * (add-season-plan-weekly-context). Carregada uma vez no {@code PlanGenerationContextLoader} e lida
 * pelo prompt, pelo motor e pela persistência — os três veem a mesma semana.
 *
 * @param weekIndex     posição da semana na temporada, a partir de 1
 * @param totalWeeks    quantidade de semanas da temporada
 * @param nextMilestone próxima prova preparatória (nesta semana ou adiante), se houver
 */
public record SeasonWeekSnapshot(UUID seasonWeekId,
                                 int weekIndex,
                                 int totalWeeks,
                                 TrainingPhase phase,
                                 @Nullable Double targetTss,
                                 @Nullable BigDecimal targetVolumeKm,
                                 @Nullable BigDecimal longRunKm,
                                 boolean deload,
                                 Optional<Milestone> nextMilestone) {

    /** Marco da temporada: prova preparatória com nome e data. */
    public record Milestone(String name, LocalDate date) {
    }
}
