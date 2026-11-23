package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.input.SeasonWeekUpdateInputDto;
import br.com.menthoros.backend.dto.output.SeasonPlanResponseDto;
import br.com.menthoros.backend.services.SeasonPlanService.Generation;

import java.util.UUID;

/**
 * Ajuste da temporada aprovada (add-season-plan-adjust-v2): rascunho de uma nova versão, edição por
 * semana e descarte. A aprovação do ajuste é a mesma rota da temporada ({@link SeasonPlanService#approve}).
 */
public interface SeasonPlanAdjustmentService {

    /**
     * Cria o rascunho da próxima versão a partir da temporada ACTIVE.
     *
     * Idempotent: YES — com rascunho aberto da mesma base devolve o mesmo (created=false).
     * Side Effects: Database insert (PlanoTreino PROPOSED + cópia das semanas); lock pessimista na prova.
     * Tenant-aware: YES
     *
     * @throws br.com.menthoros.backend.exception.SeasonPlanDisabledException flag desligada
     * @throws br.com.menthoros.backend.exception.DomainNotFoundException temporada não é do atleta no tenant
     * @throws br.com.menthoros.backend.exception.DomainConflictException a temporada não é ACTIVE, ou há
     *         outra proposta aberta do atleta
     */
    Generation createDraft(UUID atletaId, UUID seasonPlanId);

    /**
     * Uma temporada do atleta por id, rascunho incluído.
     *
     * Idempotent: YES
     * Side Effects: NONE
     * Tenant-aware: YES
     *
     * @throws br.com.menthoros.backend.exception.SeasonPlanDisabledException flag desligada
     * @throws br.com.menthoros.backend.exception.DomainNotFoundException temporada não é do atleta no tenant
     */
    SeasonPlanResponseDto findById(UUID atletaId, UUID seasonPlanId);

    /**
     * Edita uma semana de uma temporada PROPOSED; a semana passa a origem COACH.
     *
     * Idempotent: YES — reaplicar os mesmos valores deixa a semana igual.
     * Side Effects: Database update (semana e pico da temporada); lock pessimista na prova.
     * Tenant-aware: YES
     *
     * @throws br.com.menthoros.backend.exception.SeasonPlanDisabledException flag desligada
     * @throws br.com.menthoros.backend.exception.DomainNotFoundException temporada ou semana inexistente
     * @throws br.com.menthoros.backend.exception.DomainConflictException a temporada não é PROPOSED
     * @throws br.com.menthoros.backend.exception.DomainRuleViolationException semana travada, fase não
     *         permitida ou valor inválido
     */
    SeasonPlanResponseDto updateWeek(UUID atletaId, UUID seasonPlanId, int weekIndex, SeasonWeekUpdateInputDto input);

    /**
     * Descarta o rascunho de ajuste: vira ARCHIVED, sem apagar linhas.
     *
     * Idempotent: NO — descartar de novo responde 409.
     * Side Effects: Database update; lock pessimista na prova.
     * Tenant-aware: YES
     *
     * @throws br.com.menthoros.backend.exception.SeasonPlanDisabledException flag desligada
     * @throws br.com.menthoros.backend.exception.DomainNotFoundException temporada não é do atleta no tenant
     * @throws br.com.menthoros.backend.exception.DomainConflictException não é um rascunho aberto
     */
    void discard(UUID atletaId, UUID seasonPlanId);
}
