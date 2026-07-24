package br.com.menthoros.backend.dto.asaas;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Payload do webhook do Asaas (assessoria-billing-asaas, design.md Decisão 4).
 *
 * <p>{@code id} é o identificador do evento (idempotência CA10). {@code event} é o tipo
 * (PAYMENT_CONFIRMED/RECEIVED/OVERDUE, SUBSCRIPTION_DELETED/INACTIVATED). Para eventos de
 * pagamento, {@code payment.subscription} traz o id da assinatura; para eventos de assinatura,
 * {@code subscription.id}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AsaasWebhookEventDto(
        String id,
        String event,
        Payment payment,
        Subscription subscription
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payment(String id, String customer, String subscription) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Subscription(String id) {}

    /** Resolve o id da subscription do Asaas, seja de um evento de pagamento ou de assinatura. */
    public String resolverSubscriptionId() {
        if (payment != null && payment.subscription() != null) {
            return payment.subscription();
        }
        if (subscription != null) {
            return subscription.id();
        }
        return null;
    }
}
