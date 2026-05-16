package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.output.AnaliseWorkoutOutputDto;

import java.util.Optional;
import java.util.UUID;

public interface AnaliseWorkoutService {

    /**
     * Busca a análise pós-treino de um treino realizado, validando isolamento multi-tenant.
     *
     * Idempotent: YES — Read-only.
     * Side Effects: NONE
     * Tenant-aware: YES
     *
     * @param treinoRealizadoId ID do treino realizado
     * @param tenantId          ID do tenant autenticado
     * @return Optional com a análise, ou empty se não encontrada / ainda pendente
     * @throws br.com.menthoros.backend.exception.DomainNotFoundException se o treino não pertence ao tenant
     */
    Optional<AnaliseWorkoutOutputDto> buscarAnalise(UUID treinoRealizadoId, UUID tenantId);
}
