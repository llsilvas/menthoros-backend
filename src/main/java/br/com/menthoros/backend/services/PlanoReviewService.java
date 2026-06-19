package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.output.PlanoSemanalOutputDto;

import java.util.List;
import java.util.UUID;

public interface PlanoReviewService {

    /**
     * Lista todos os planos do tenant com reviewStatus = AGUARDANDO_REVISAO, ordenados por semanaInicio ASC.
     *
     * Idempotent: YES — leitura pura.
     * Side Effects: NONE
     * Tenant-aware: YES
     *
     * @param tenantId ID da assessoria (tenant)
     * @return lista de planos pendentes de revisão, mais antigos primeiro
     */
    List<PlanoSemanalOutputDto> listarPlanosPendentes(UUID tenantId);

    /**
     * Aprova um plano, alterando reviewStatus de AGUARDANDO_REVISAO para APROVADO.
     *
     * Idempotent: NO — transição de estado; chamar duas vezes lança DomainRuleViolationException.
     * Side Effects: Database update (reviewStatus = APROVADO)
     * Tenant-aware: YES
     *
     * @param planoId  ID do PlanoSemanal a aprovar
     * @param tenantId ID do tenant do coach
     * @return plano atualizado com reviewStatus = APROVADO
     * @throws br.com.menthoros.backend.exception.DomainNotFoundException    se o plano não existe no tenant
     * @throws br.com.menthoros.backend.exception.DomainRuleViolationException se a transição é ilegal
     */
    PlanoSemanalOutputDto aprovarPlano(UUID planoId, UUID tenantId);

    /**
     * Rejeita um plano, alterando reviewStatus de AGUARDANDO_REVISAO para REJEITADO.
     *
     * Idempotent: NO — transição de estado; chamar duas vezes lança DomainRuleViolationException.
     * Side Effects: Database update (reviewStatus = REJEITADO, reviewComment = motivo)
     * Tenant-aware: YES
     *
     * @param planoId  ID do PlanoSemanal a rejeitar
     * @param tenantId ID do tenant do coach
     * @param motivo   motivo da rejeição (não-nulo, não-vazio)
     * @return plano atualizado com reviewStatus = REJEITADO
     * @throws br.com.menthoros.backend.exception.DomainNotFoundException    se o plano não existe no tenant
     * @throws br.com.menthoros.backend.exception.DomainRuleViolationException se a transição é ilegal
     */
    PlanoSemanalOutputDto rejeitarPlano(UUID planoId, UUID tenantId, String motivo);
}
