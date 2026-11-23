package br.com.menthoros.backend.domain.planner;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Semana da temporada que o ajuste não pode mudar (add-season-plan-adjust-v2): o passado e a semana
 * corrente já foram (ou estão sendo) executados, e a semana da prova-alvo é fixa. Marcos preparatórios
 * não travam.
 */
public final class SeasonWeekLock {

    /** Por que a semana está travada; a primeira regra que se aplica vence, nesta ordem. */
    public enum Reason {
        COMPLETED,
        IN_PROGRESS,
        RACE_WEEK
    }

    private SeasonWeekLock() {
    }

    /**
     * @param weekStart segunda-feira da semana
     * @param today     hoje no fuso da assessoria ({@code America/Sao_Paulo})
     * @param raceDate  data da prova-alvo, se houver
     */
    public static Optional<Reason> of(LocalDate weekStart, LocalDate today, @Nullable LocalDate raceDate) {
        LocalDate nextWeek = weekStart.plusWeeks(1);
        if (!nextWeek.isAfter(today)) {
            return Optional.of(Reason.COMPLETED);
        }
        if (!weekStart.isAfter(today)) {
            return Optional.of(Reason.IN_PROGRESS);
        }
        if (raceDate != null && !raceDate.isBefore(weekStart) && raceDate.isBefore(nextWeek)) {
            return Optional.of(Reason.RACE_WEEK);
        }
        return Optional.empty();
    }
}
