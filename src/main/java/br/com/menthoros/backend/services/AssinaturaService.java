package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.input.AssinaturaInputDto;
import br.com.menthoros.backend.dto.input.AssinaturaTierInputDto;
import br.com.menthoros.backend.dto.output.AssinaturaOutputDto;
import br.com.menthoros.backend.exception.DomainConflictException;
import br.com.menthoros.backend.exception.DomainNotFoundException;

import java.util.UUID;

/**
 * Service de cobrança B2B de assessorias via Asaas (assessoria-billing-asaas).
 */
public interface AssinaturaService {

    /**
     * Cria a assinatura de uma assessoria: grava a âncora local {@code PENDENTE}, cria cliente+
     * assinatura no Asaas e confirma para {@code ATIVA} (design.md Decisão 9 / CA1/CA13/CA14).
     *
     * <p><strong>Idempotent:</strong> PARCIAL — retry sobre uma assinatura {@code PENDENTE} retoma
     * a criação (não duplica); um POST sobre assinatura já ativa lança conflito (CA14).
     * <p><strong>Side Effects:</strong> DB (Assinatura + Assessoria.plano) + External API (Asaas).
     * <p><strong>Tenant-aware:</strong> NO — operação administrativa (ADMIN).
     *
     * @throws DomainNotFoundException se a assessoria não existir
     * @throws DomainConflictException se a assessoria já tiver assinatura não-{@code PENDENTE}
     */
    AssinaturaOutputDto criar(UUID assessoriaId, AssinaturaInputDto input);

    /**
     * Troca o tier: atualiza o entitlement local e o valor no Asaas na mesma transação, com a
     * chamada externa por último — falha do Asaas reverte o lado local (CA9/CA15).
     *
     * <p><strong>Idempotent:</strong> NO — cada chamada aplica um novo tier/valor.
     * <p><strong>Side Effects:</strong> DB (Assessoria.plano + Assinatura.valor) + External API (Asaas).
     * <p><strong>Tenant-aware:</strong> NO — operação administrativa (ADMIN).
     *
     * @throws DomainNotFoundException se a assessoria/assinatura não existir
     * @throws DomainConflictException se a assinatura não tiver subscription ativa no Asaas
     */
    AssinaturaOutputDto atualizarTier(UUID assessoriaId, AssinaturaTierInputDto input);

    /**
     * Cancela a assinatura: marca {@code CANCELADA} + {@code Assessoria.ativo=false} e cancela no
     * Asaas, com a chamada externa por último (CA7/CA15).
     *
     * <p><strong>Idempotent:</strong> NO — cancelar de novo tentaria cancelar no Asaas novamente.
     * <p><strong>Side Effects:</strong> DB (Assinatura.status + Assessoria.ativo) + External API (Asaas).
     * <p><strong>Tenant-aware:</strong> NO — operação administrativa (ADMIN).
     *
     * @throws DomainNotFoundException se a assessoria/assinatura não existir
     */
    void cancelar(UUID assessoriaId);
}
