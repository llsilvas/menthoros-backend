package br.com.menthoros.backend.enums;

import java.time.LocalDate;
import java.util.List;

/**
 * Status de cobrança do atleta, derivado em leitura das mensalidades em aberto — nunca
 * persistido (design D4). Substitui {@code StatusVencimentoPlano}, que derivava de uma data solta.
 * Visível a todo treinador; nunca carrega valor.
 */
public enum AthleteBillingStatus {

    UP_TO_DATE,
    DUE_SOON,
    OVERDUE;

    public static final int DUE_SOON_WINDOW_DAYS = 7;

    /**
     * OVERDUE se alguma mensalidade em aberto venceu antes de hoje; DUE_SOON se a mais próxima
     * vence em até {@value #DUE_SOON_WINDOW_DAYS} dias; UP_TO_DATE caso contrário — inclusive
     * sem nenhuma mensalidade em aberto (quem decide se o status existe é o serviço, pelo contrato).
     */
    public static AthleteBillingStatus resolve(List<LocalDate> openDueDates, LocalDate today) {
        if (today == null) {
            throw new IllegalArgumentException("today cannot be null");
        }
        if (openDueDates == null || openDueDates.isEmpty()) {
            return UP_TO_DATE;
        }
        LocalDate earliest = null;
        for (LocalDate dueDate : openDueDates) {
            if (dueDate == null) {
                continue;
            }
            if (dueDate.isBefore(today)) {
                return OVERDUE;
            }
            if (earliest == null || dueDate.isBefore(earliest)) {
                earliest = dueDate;
            }
        }
        if (earliest != null && !earliest.isAfter(today.plusDays(DUE_SOON_WINDOW_DAYS))) {
            return DUE_SOON;
        }
        return UP_TO_DATE;
    }
}
