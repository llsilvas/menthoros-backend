package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.enums.ContractPeriodicity;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Aritmética de vencimento do contrato do atleta (design D2). Pura, sem Spring.
 *
 * <p>O clamp usa sempre o {@code dueDay} do contrato, nunca o dia do vencimento anterior: dia
 * 31 → 28 (fev) → 31 (mar), e não 28 para sempre.</p>
 */
public final class DueDateCalendar {

    private DueDateCalendar() {
    }

    /**
     * Primeiro dia {@code dueDay} maior ou igual a {@code start}: no mesmo mês se ainda não
     * passou, senão no mês seguinte — com clamp ao último dia do mês.
     */
    public static LocalDate firstDueDate(LocalDate start, int dueDay) {
        validateDueDay(dueDay);
        if (start == null) {
            throw new IllegalArgumentException("start cannot be null");
        }
        LocalDate sameMonth = clamp(YearMonth.from(start), dueDay);
        if (!sameMonth.isBefore(start)) {
            return sameMonth;
        }
        return clamp(YearMonth.from(start).plusMonths(1), dueDay);
    }

    /** Vencimento anterior + N meses da periodicidade, no dia do contrato com clamp. */
    public static LocalDate nextDueDate(LocalDate previous, ContractPeriodicity periodicity, int dueDay) {
        validateDueDay(dueDay);
        if (previous == null || periodicity == null) {
            throw new IllegalArgumentException("previous and periodicity cannot be null");
        }
        return clamp(YearMonth.from(previous).plusMonths(periodicity.months()), dueDay);
    }

    private static LocalDate clamp(YearMonth month, int dueDay) {
        return month.atDay(Math.min(dueDay, month.lengthOfMonth()));
    }

    private static void validateDueDay(int dueDay) {
        if (dueDay < 1 || dueDay > 31) {
            throw new IllegalArgumentException("dueDay must be between 1 and 31, got " + dueDay);
        }
    }
}
