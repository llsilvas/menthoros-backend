package br.com.menthoros.backend.domain.billing;

import br.com.menthoros.backend.enums.AthleteBillingStatus;

import java.time.LocalDate;

/**
 * O que todo treinador vê sobre a cobrança de um atleta: status derivado e próximo vencimento.
 * Sem valor, por desenho (design D5). Ausente quando o atleta não tem contrato ativo nem
 * mensalidade em aberto.
 *
 * @param nextDueDate menor vencimento em aberto, ou o próximo calculado quando não há em aberto
 */
public record AthleteBilling(AthleteBillingStatus status, LocalDate nextDueDate) {

    public AthleteBilling {
        if (status == null || nextDueDate == null) {
            throw new IllegalArgumentException("status and nextDueDate cannot be null");
        }
    }
}
