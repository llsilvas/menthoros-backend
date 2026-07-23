package br.com.menthoros.backend.enums;

/**
 * Estado da cobrança de uma {@code Assinatura} ({@code tb_assinatura}, assessoria-billing-asaas).
 *
 * <p>Serializado como o nome (string) — o contrato é uma união de strings, não um enum-objeto.
 * Sem estado {@code TRIAL} (ver {@code docs/adr/0005}): trial é uma {@link #ATIVA} comum com
 * primeira cobrança agendada no futuro.
 *
 * <p>Transições (design.md Decisão 2 + 9):
 * <ul>
 *   <li>{@code — → PENDENTE}: POST cria a âncora local antes de chamar o Asaas (CA13).</li>
 *   <li>{@code PENDENTE → ATIVA}: Asaas confirma customer+subscription (CA1/CA13).</li>
 *   <li>{@code ATIVA → INADIMPLENTE}: webhook {@code PAYMENT_OVERDUE} (CA3).</li>
 *   <li>{@code INADIMPLENTE → ATIVA}: pagamento confirmado dentro da carência (CA4).</li>
 *   <li>{@code INADIMPLENTE → SUSPENSA}: job diário, > 5 dias corridos (CA5).</li>
 *   <li>{@code SUSPENSA → ATIVA}: pagamento resolvido após suspensão (CA6).</li>
 *   <li>{@code qualquer → CANCELADA}: DELETE admin (CA7) ou reconciliação via webhook (CA8).</li>
 * </ul>
 *
 * <p>{@link #PENDENTE} é estado transitório de criação (âncora local da estratégia de falha
 * parcial), não um estado de cobrança de negócio.
 */
public enum StatusAssinatura {
    PENDENTE,
    ATIVA,
    INADIMPLENTE,
    SUSPENSA,
    CANCELADA
}
