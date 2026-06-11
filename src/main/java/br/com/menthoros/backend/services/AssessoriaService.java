package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.input.AssessoriaInputDto;
import br.com.menthoros.backend.dto.output.AssessoriaOutputDto;
import br.com.menthoros.backend.exception.DuplicateResourceException;

/**
 * Service de domínio para gestão de assessorias (tenants).
 */
public interface AssessoriaService {

    /**
     * Cria uma nova assessoria e a Organization correspondente no Keycloak.
     *
     * Idempotent: NO — Cria uma nova assessoria e Organization a cada chamada.
     * Side Effects: Database insert (Assessoria) + criação de Organization no Keycloak.
     * Tenant-aware: N/A (operação administrativa).
     *
     * @param input dados de entrada da assessoria
     * @return DTO de saída da assessoria criada
     * @throws DuplicateResourceException se já existir assessoria com o domínio informado
     */
    AssessoriaOutputDto criarAssessoria(AssessoriaInputDto input);
}
