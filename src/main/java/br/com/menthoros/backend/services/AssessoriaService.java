package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.input.AssessoriaInputDto;
import br.com.menthoros.backend.dto.output.AssessoriaOutputDto;

/**
 * Service de domínio para gestão de assessorias (tenants).
 */
public interface AssessoriaService {

    /**
     * Cria uma nova assessoria e a Organization correspondente no Keycloak.
     *
     * @param input dados de entrada da assessoria
     * @return DTO de saída da assessoria criada
     */
    AssessoriaOutputDto criarAssessoria(AssessoriaInputDto input);
}
