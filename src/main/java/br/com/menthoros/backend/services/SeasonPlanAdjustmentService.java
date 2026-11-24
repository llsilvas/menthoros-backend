package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.input.SeasonPlanRecalculateInputDto;
import br.com.menthoros.backend.dto.input.SeasonWeekUpdateInputDto;
import br.com.menthoros.backend.dto.output.SeasonPlanDiffDto;
import br.com.menthoros.backend.dto.output.SeasonPlanResponseDto;
import br.com.menthoros.backend.services.SeasonPlanService.Generation;

import java.util.List;
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
     * Regenera pelo motor as semanas livres do rascunho de ajuste, com as semanas por fase e o volume de
     * partida pedidos. Semanas travadas e as editadas pelo treinador (COACH) ficam como estão; os marcos
     * são os já gravados no rascunho.
     *
     * Idempotent: YES — a mesma entrada sobre o mesmo rascunho gera as mesmas semanas.
     * Side Effects: Database update (semanas livres, volume de partida e picos do rascunho); lock pessimista na prova.
     * Tenant-aware: YES
     *
     * @throws br.com.menthoros.backend.exception.SeasonPlanDisabledException flag desligada
     * @throws br.com.menthoros.backend.exception.DomainNotFoundException temporada não é do atleta no tenant
     * @throws br.com.menthoros.backend.exception.DomainConflictException não é um rascunho de ajuste aberto
     * @throws br.com.menthoros.backend.exception.DomainRuleViolationException fases ou volume inválidos, soma
     *         diferente do ciclo, prova remarcada, CTL de partida ausente ou fase divergente numa semana travada
     */
    SeasonPlanResponseDto recalculate(UUID atletaId, UUID seasonPlanId, SeasonPlanRecalculateInputDto input);

    /**
     * Campos alterados do rascunho de ajuste em relação à temporada ativa de que ele partiu, por semana.
     *
     * Idempotent: YES
     * Side Effects: NONE
     * Tenant-aware: YES
     *
     * @throws br.com.menthoros.backend.exception.SeasonPlanDisabledException flag desligada
     * @throws br.com.menthoros.backend.exception.DomainNotFoundException temporada não é do atleta no tenant
     * @throws br.com.menthoros.backend.exception.DomainConflictException não é um rascunho de ajuste aberto
     */
    List<SeasonPlanDiffDto> diff(UUID atletaId, UUID seasonPlanId);

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
