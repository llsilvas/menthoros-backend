package br.com.menthoros.backend.services;

import br.com.menthoros.backend.entity.Assessoria;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Porta de saída para a API do Asaas (assessoria-billing-asaas, design.md Decisão 3).
 *
 * <p>Isola o {@code AssinaturaService} do detalhe HTTP do Asaas. Toda falha externa é
 * sinalizada como {@code AsaasIntegrationException} (mapeada para 502).
 */
public interface AsaasGateway {

    /**
     * Cria (ou reaproveita, por {@code externalReference=assessoriaId}) o cliente no Asaas e cria a
     * assinatura de cartão com cobrança diferida em {@code nextDueDate} (CA1/CA12/CA14).
     *
     * @param assessoria       assessoria pagante (nome/razão social, cnpj, email, id como externalReference)
     * @param creditCardToken  token de cartão pré-tokenizado do Asaas (nunca PAN/CVV bruto — PCI, Decisão 3)
     * @param nextDueDate      data da primeira cobrança (futuro suporta trial)
     * @param valor            valor mensal da assinatura
     * @return ids do cliente e da assinatura criados no Asaas
     */
    AsaasAssinaturaCriada criarClienteEAssinatura(
            Assessoria assessoria, String creditCardToken, LocalDate nextDueDate, BigDecimal valor);

    /**
     * Atualiza o valor da assinatura no Asaas (troca de tier — CA9), afetando mensalidades futuras.
     */
    void atualizarValor(String asaasSubscriptionId, BigDecimal novoValor);

    /**
     * Remove a assinatura no Asaas, encerrando a recorrência (CA7).
     */
    void cancelarAssinatura(String asaasSubscriptionId);

    /** Resultado da criação no Asaas. */
    record AsaasAssinaturaCriada(String asaasCustomerId, String asaasSubscriptionId, String status) {}
}
