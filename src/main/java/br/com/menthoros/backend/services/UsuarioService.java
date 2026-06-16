package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.output.UsuarioMeOutputDto;

/**
 * Serviço de identidade do usuário autenticado.
 */
public interface UsuarioService {

    /**
     * Resolve a identidade do usuário autenticado no tenant atual.
     *
     * Idempotent: YES — Read-only, sem mutação de estado.
     * Side Effects: NONE
     * Tenant-aware: YES — resolve pelo tenant corrente e usa queries tenant-scoped.
     *
     * @return identidade do usuário (inclui atletaId quando role ATLETA com vínculo)
     */
    UsuarioMeOutputDto getCurrentUser();
}
