package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.output.AthleteSeasonPlanDto;
import br.com.menthoros.backend.dto.output.SeasonPlanResponseDto;

import org.jspecify.annotations.Nullable;

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
     * Idempotent: YES — com PROPOSED existente para a mesma prova, devolve a mesma (created=false), exceto com o
     *         atleta em calibração: aí responde 422 mesmo havendo proposta (o gate vem antes).
     * Side Effects: Database insert (PlanoTreino + semanas); lock pessimista na prova-alvo.
     * Tenant-aware: YES
     *
     * @throws br.com.menthoros.backend.exception.SeasonPlanDisabledException flag desligada
     * @throws br.com.menthoros.backend.exception.DomainNotFoundException atleta fora do tenant
     * @throws br.com.menthoros.backend.exception.DomainConflictException temporada ACTIVE, ou PROPOSED de outra prova
     * @throws br.com.menthoros.backend.exception.DomainRuleViolationException sem prova-alvo válida, horizonte
     *         menor que 2 semanas, base de partida faltando, ou atleta em calibração com
     *         {@code planner-engine.shadow} ligado (add-season-plan-calibration-gate)
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

    /**
     * Aprova a temporada PROPOSED do atleta: vira ACTIVE com quem e quando aprovou
     * (add-season-plan-approve-athlete-view, A1, A3, A5). Um ajuste (PROPOSED com base, versão 2 em
     * diante) exige motivo, arquiva a ACTIVE anterior e recopia dela as semanas travadas
     * (add-season-plan-adjust-v2).
     *
     * Idempotent: YES — aprovar uma ACTIVE devolve a mesma, sem reescrever {@code approvedBy}/{@code approvedAt}.
     * Side Effects: Database update (status e auditoria da temporada); lock pessimista na prova da temporada.
     * Tenant-aware: YES
     *
     * @throws br.com.menthoros.backend.exception.SeasonPlanDisabledException flag desligada
     * @throws br.com.menthoros.backend.exception.DomainNotFoundException temporada não é do atleta no tenant, ou
     *         usuário autenticado fora do tenant
     * @throws br.com.menthoros.backend.exception.DomainConflictException temporada ARCHIVED/COMPLETED, ou outra
     *         ACTIVE do atleta, ou (ajuste) a ACTIVE mudou depois de o ajuste começar
     * @throws br.com.menthoros.backend.exception.DomainRuleViolationException a prova deixou de ser alvo ou foi
     *         cancelada, o atleta está em calibração com {@code planner-engine.shadow} ligado, ou (ajuste) o
     *         motivo falta, está em branco ou passa de 255 caracteres
     */
    SeasonPlanResponseDto approve(UUID atletaId, UUID seasonPlanId, @Nullable String reason);

    /**
     * Temporada ACTIVE do atleta autenticado, enquanto a prova não passou (A2). Vazio também com a flag
     * desligada (A6): o atleta não precisa saber que existe flag.
     *
     * Idempotent: YES
     * Side Effects: NONE
     * Tenant-aware: YES — o atleta vem do JWT, nunca de parâmetro.
     *
     * @throws br.com.menthoros.backend.exception.DomainNotFoundException usuário sem atleta no tenant
     */
    Optional<AthleteSeasonPlanDto> findForAuthenticatedAthlete();
}
