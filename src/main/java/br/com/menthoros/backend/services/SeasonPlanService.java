package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.output.SeasonPlanResponseDto;

import java.util.Optional;
import java.util.UUID;

/** Plano de temporada do atleta (add-season-plan-generate-tracer). */
public interface SeasonPlanService {

    /** Temporada devolvida pela geração e se ela acabou de nascer (201) ou já existia (200). */
    record Generation(SeasonPlanResponseDto plan, boolean created) {
    }

    /**
     * Gera a temporada PROPOSED da prova-alvo do atleta.
     *
     * Idempotent: YES — com PROPOSED existente para a mesma prova, devolve a mesma (created=false).
     * Side Effects: Database insert (PlanoTreino + semanas); lock pessimista na prova-alvo.
     * Tenant-aware: YES
     *
     * @throws br.com.menthoros.backend.exception.SeasonPlanDisabledException flag desligada
     * @throws br.com.menthoros.backend.exception.DomainNotFoundException atleta fora do tenant
     * @throws br.com.menthoros.backend.exception.DomainConflictException temporada ACTIVE, ou PROPOSED de outra prova
     * @throws br.com.menthoros.backend.exception.DomainRuleViolationException sem prova-alvo válida, horizonte
     *         menor que 2 semanas, ou base de partida faltando
     */
    Generation generate(UUID atletaId);

    /**
     * Temporada ACTIVE do atleta ou, se não houver, a PROPOSED.
     *
     * Idempotent: YES
     * Side Effects: NONE
     * Tenant-aware: YES
     *
     * @throws br.com.menthoros.backend.exception.SeasonPlanDisabledException flag desligada
     * @throws br.com.menthoros.backend.exception.DomainNotFoundException atleta fora do tenant
     */
    Optional<SeasonPlanResponseDto> findCurrent(UUID atletaId);
}
