package br.com.menthoros.backend.enums;

/**
 * Estado persistido da mensalidade. "Vencida" não é estado: é {@code OPEN} com vencimento no
 * passado, derivado em leitura por {@code AthleteBillingStatus}.
 */
public enum InvoiceStatus {
    OPEN,
    PAID,
    CANCELLED
}
