package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.exception.AsaasIntegrationException;
import br.com.menthoros.backend.services.AsaasGateway;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Adapter real do {@link AsaasGateway} via API do Asaas v3 (Spring {@link RestClient}).
 *
 * <p>Autenticação por header {@code access_token} (default header do {@code asaasRestClient}).
 * Segredos (API key) e o token de cartão nunca são logados nem incluídos em mensagem de exceção.
 */
@Slf4j
@Service
public class AsaasGatewayImpl implements AsaasGateway {

    private static final String CYCLE_MENSAL = "MONTHLY";
    private static final String BILLING_CARTAO = "CREDIT_CARD";

    private final RestClient restClient;

    public AsaasGatewayImpl(RestClient asaasRestClient) {
        this.restClient = asaasRestClient;
    }

    @Override
    public AsaasAssinaturaCriada criarClienteEAssinatura(
            Assessoria assessoria, String creditCardToken, LocalDate nextDueDate, BigDecimal valor) {
        UUID assessoriaId = assessoria.getId();
        log.info("Asaas: criando cliente+assinatura para assessoriaId={}, nextDueDate={}", assessoriaId, nextDueDate);

        String customerId = buscarOuCriarCliente(assessoria);
        SubscriptionResponse subscription = criarAssinatura(customerId, creditCardToken, nextDueDate, valor, assessoriaId);

        log.info("Asaas: assinatura criada assessoriaId={}, customerId={}, subscriptionId={}, status={}",
                assessoriaId, customerId, subscription.id(), subscription.status());
        return new AsaasAssinaturaCriada(customerId, subscription.id(), subscription.status());
    }

    @Override
    public void atualizarValor(String asaasSubscriptionId, BigDecimal novoValor) {
        log.info("Asaas: atualizando valor da assinatura subscriptionId={}", asaasSubscriptionId);
        Map<String, Object> body = Map.of("value", novoValor, "updatePendingPayments", true);
        try {
            restClient.put()
                    .uri("/subscriptions/{id}", asaasSubscriptionId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            throw falha("atualizar valor da assinatura (subscriptionId=" + asaasSubscriptionId + ")", e);
        }
    }

    @Override
    public void cancelarAssinatura(String asaasSubscriptionId) {
        log.info("Asaas: cancelando assinatura subscriptionId={}", asaasSubscriptionId);
        try {
            restClient.delete()
                    .uri("/subscriptions/{id}", asaasSubscriptionId)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            throw falha("cancelar assinatura (subscriptionId=" + asaasSubscriptionId + ")", e);
        }
    }

    /**
     * Idempotência (CA14): reaproveita o cliente já criado para a assessoria (lookup por
     * {@code externalReference}) antes de criar um novo — evita cliente duplicado no Asaas em retry.
     */
    private String buscarOuCriarCliente(Assessoria assessoria) {
        UUID assessoriaId = assessoria.getId();
        String externalReference = assessoriaId.toString();

        try {
            CustomerListResponse existentes = restClient.get()
                    .uri(b -> b.path("/customers").queryParam("externalReference", externalReference).build())
                    .retrieve()
                    .body(CustomerListResponse.class);

            if (existentes != null && existentes.data() != null && !existentes.data().isEmpty()) {
                String customerId = existentes.data().get(0).id();
                log.info("Asaas: cliente reaproveitado assessoriaId={}, customerId={}", assessoriaId, customerId);
                return customerId;
            }
        } catch (Exception e) {
            throw falha("buscar cliente por externalReference (assessoriaId=" + assessoriaId + ")", e);
        }

        Map<String, Object> body = new HashMap<>();
        body.put("name", assessoria.getRazaoSocial() != null ? assessoria.getRazaoSocial() : assessoria.getNome());
        body.put("externalReference", externalReference);
        if (assessoria.getCnpj() != null) {
            body.put("cpfCnpj", assessoria.getCnpj());
        }
        if (assessoria.getEmailContato() != null) {
            body.put("email", assessoria.getEmailContato());
        }

        try {
            CustomerResponse criado = restClient.post()
                    .uri("/customers")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(CustomerResponse.class);

            if (criado == null || criado.id() == null || criado.id().isBlank()) {
                throw new AsaasIntegrationException(
                        "Asaas não retornou id ao criar cliente (assessoriaId=" + assessoriaId + ")");
            }
            log.info("Asaas: cliente criado assessoriaId={}, customerId={}", assessoriaId, criado.id());
            return criado.id();
        } catch (AsaasIntegrationException e) {
            throw e;
        } catch (Exception e) {
            throw falha("criar cliente (assessoriaId=" + assessoriaId + ")", e);
        }
    }

    private SubscriptionResponse criarAssinatura(
            String customerId, String creditCardToken, LocalDate nextDueDate, BigDecimal valor, UUID assessoriaId) {
        Map<String, Object> body = new HashMap<>();
        body.put("customer", customerId);
        body.put("billingType", BILLING_CARTAO);
        body.put("creditCardToken", creditCardToken);
        body.put("value", valor);
        body.put("nextDueDate", nextDueDate.toString());
        body.put("cycle", CYCLE_MENSAL);
        body.put("externalReference", assessoriaId.toString());

        try {
            SubscriptionResponse subscription = restClient.post()
                    .uri("/subscriptions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(SubscriptionResponse.class);

            if (subscription == null || subscription.id() == null || subscription.id().isBlank()) {
                throw new AsaasIntegrationException(
                        "Asaas não retornou id ao criar assinatura (assessoriaId=" + assessoriaId + ")");
            }
            return subscription;
        } catch (AsaasIntegrationException e) {
            throw e;
        } catch (Exception e) {
            throw falha("criar assinatura (assessoriaId=" + assessoriaId + ")", e);
        }
    }

    /** Envolve a falha sem vazar API key/token de cartão (a mensagem original do RestClient pode conter headers). */
    private AsaasIntegrationException falha(String operacao, Throwable causa) {
        log.error("Falha na integração com o Asaas ao {}", operacao, causa);
        return new AsaasIntegrationException("Falha na integração com o Asaas ao " + operacao);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CustomerListResponse(List<CustomerResponse> data, Integer totalCount) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CustomerResponse(String id) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SubscriptionResponse(String id, String status) {}
}
