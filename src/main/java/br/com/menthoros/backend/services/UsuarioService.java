package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.input.ConsentInputDto;
import br.com.menthoros.backend.dto.output.UsuarioMeOutputDto;

/**
 * Serviço de identidade do usuário autenticado.
 */
public interface UsuarioService {

    /**
     * Registra o consentimento LGPD do usuário autenticado para as versões vigentes dos documentos.
     *
     * Idempotent: YES — reenvio das mesmas versões é no-op; a constraint única do banco arbitra a
     *   corrida de aceites simultâneos.
     * Side Effects: Database insert (tb_usuario_lgpd_consent) — nunca update, nunca delete.
     * Tenant-aware: YES — resolve o caller pelo sub do JWT e valida que o tenant do usuário bate
     *   com o TenantContext antes de gravar.
     *
     * @param input aceite dos dois documentos + versões que o cliente renderizou
     * @throws br.com.menthoros.backend.exception.DomainNotFoundException     usuário inexistente no tenant
     * @throws br.com.menthoros.backend.exception.ConsentVersionStaleException versões declaradas não são as vigentes
     */
    void registerConsent(ConsentInputDto input);

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
