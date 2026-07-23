package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.services.AsaasGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Implementação MOCK do {@link AsaasGateway} — usada enquanto ainda não há ligação real com o
 * provider (Asaas). Ativa por default ({@code asaas.mock=true} ou ausente); o {@code AsaasGatewayImpl}
 * real só assume quando {@code asaas.mock=false}.
 *
 * <p>Não faz chamada de rede: devolve ids determinísticos derivados do {@code assessoriaId} para que
 * o fluxo local (âncora PENDENTE → ATIVA, endpoints admin, webhook simulado) funcione ponta a ponta
 * sem uma conta Asaas. Nunca loga o token de cartão.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "asaas", name = "mock", havingValue = "true", matchIfMissing = true)
public class AsaasGatewayMock implements AsaasGateway {

    @Override
    public AsaasAssinaturaCriada criarClienteEAssinatura(
            Assessoria assessoria, String creditCardToken, LocalDate nextDueDate, BigDecimal valor) {
        String customerId = "cus_mock_" + assessoria.getId();
        String subscriptionId = "sub_mock_" + assessoria.getId();
        log.warn("[asaas-mock] criarClienteEAssinatura assessoriaId={}, nextDueDate={}, valor={} -> customerId={}, subscriptionId={}",
                assessoria.getId(), nextDueDate, valor, customerId, subscriptionId);
        return new AsaasAssinaturaCriada(customerId, subscriptionId, "ACTIVE");
    }

    @Override
    public void atualizarValor(String asaasSubscriptionId, BigDecimal novoValor) {
        log.warn("[asaas-mock] atualizarValor subscriptionId={}, novoValor={}", asaasSubscriptionId, novoValor);
    }

    @Override
    public void cancelarAssinatura(String asaasSubscriptionId) {
        log.warn("[asaas-mock] cancelarAssinatura subscriptionId={}", asaasSubscriptionId);
    }
}
