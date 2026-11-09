package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.entity.UsuarioTrajetoShareConsent;
import org.springframework.data.repository.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Registro append-only: só inserir e ler. Estende {@link Repository} (não {@code JpaRepository})
 * para que update e delete nem existam no contrato.
 */
public interface UsuarioTrajetoShareConsentRepository extends Repository<UsuarioTrajetoShareConsent, UUID> {

    UsuarioTrajetoShareConsent save(UsuarioTrajetoShareConsent evento);

    Optional<UsuarioTrajetoShareConsent> findFirstByUsuario_IdAndTenantIdOrderBySeqDesc(UUID usuarioId, UUID tenantId);

    long countByUsuario_IdAndTenantId(UUID usuarioId, UUID tenantId);
}
