package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.output.PlanoSemanalOutputDto;
import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.enums.PlanoReviewStatus;

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

    /**
     * Lista planos do tenant filtrados por reviewStatus, ordenados por semanaInicio ASC.
     *
     * Idempotent: YES — leitura pura.
     * Side Effects: NONE
     * Tenant-aware: YES
     *
     * @param tenantId    ID da assessoria (tenant)
     * @param reviewStatus status de revisão desejado
     * @return lista de planos com o status informado, mais antigos primeiro
     */
    List<PlanoSemanalOutputDto> listarPlanosPorStatus(UUID tenantId, PlanoReviewStatus reviewStatus);

    /**
     * Aplica os efeitos de aprovação (status, comment, save, evento) a um {@link PlanoSemanal}
     * já carregado em memória — sem re-fetch por ID nem validação de transição (o chamador é
     * responsável por garantir que a transição faz sentido). Extraído de {@link #aprovarPlano}
     * para ser reutilizado pelo auto-approve do onboarding (athlete-onboarding-baseline, CA5),
     * garantindo que planos que nascem já aprovados publiquem o MESMO {@code PlanoAprovadoEvent}
     * do fluxo manual — sem isso, a sincronização com intervals.icu nunca dispararia para eles.
     *
     * Idempotent: NÃO — altera o estado do plano e publica evento a cada chamada.
     * Side Effects: Database update (reviewStatus = APROVADO, reviewComment = null) + save +
     * publica {@link br.com.menthoros.backend.events.PlanoAprovadoEvent}.
     * Tenant-aware: SIM — {@code tenantId} explícito, usado no evento.
     *
     * @param plano    plano já carregado (novo ou existente) a aprovar
     * @param tenantId tenant do plano
     * @return o plano salvo, com associações inicializadas
     */
    PlanoSemanal aprovarTransicao(PlanoSemanal plano, UUID tenantId);
}
