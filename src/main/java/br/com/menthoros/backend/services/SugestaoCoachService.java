package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.output.SugestaoCoachOutputDto;
import br.com.menthoros.backend.enums.StatusSugestao;

import java.util.List;
import java.util.UUID;

/**
 * Gerencia o ciclo de vida das sugestões de IA geradas para revisão do treinador.
 * Todos os métodos são tenant-aware (resolvem tenant via TenantContext).
 */
public interface SugestaoCoachService {

    /**
     * Lista sugestões do tenant filtradas por status.
     * Idempotent: YES. Side Effects: NONE. Tenant-aware: YES.
     *
     * <p>Quando {@code status == PENDING}, exclui sugestões com {@code expiresAt} no passado.
     *
     * @param status filtro de status; null retorna todas as sugestões do tenant
     */
    List<SugestaoCoachOutputDto> listar(StatusSugestao status);

    /**
     * Retorna o detalhe de uma sugestão.
     * Idempotent: YES. Side Effects: NONE. Tenant-aware: YES.
     *
     * @throws br.com.menthoros.backend.exception.DomainNotFoundException se não encontrada no tenant
     */
    SugestaoCoachOutputDto detalhe(UUID id);

    /**
     * Lista todas as sugestões de um atleta específico do tenant, ordenadas por data de criação DESC.
     * Idempotent: YES. Side Effects: NONE. Tenant-aware: YES.
     *
     * @param atletaId ID do atleta
     */
    List<SugestaoCoachOutputDto> listarPorAtleta(UUID atletaId);

    /**
     * Aprova uma sugestão PENDING, transicionando para APPROVED.
     * Idempotent: YES (aprovar já-APPROVED é no-op). Side Effects: DB update. Tenant-aware: YES.
     *
     * @throws br.com.menthoros.backend.exception.DomainNotFoundException se não encontrada
     * @throws br.com.menthoros.backend.exception.DomainRuleViolationException se status == REJECTED (422)
     */
    SugestaoCoachOutputDto aprovar(UUID id);

    /**
     * Rejeita uma sugestão PENDING, transicionando para REJECTED.
     * Idempotent: YES (re-rejeitar já-REJECTED é no-op). Side Effects: DB update. Tenant-aware: YES.
     *
     * @throws br.com.menthoros.backend.exception.DomainNotFoundException se não encontrada
     * @throws br.com.menthoros.backend.exception.DomainRuleViolationException se status == APPROVED (422)
     */
    SugestaoCoachOutputDto rejeitar(UUID id);
}
