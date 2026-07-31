package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.entity.UsuarioLgpdConsent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Acesso ao registro append-only de consentimento LGPD.
 *
 * <p><b>Contrato:</b> esta tabela só recebe insert. Não adicionar aqui método de update ou delete —
 * sobrescrever ou apagar um aceite destrói a prova de qual texto foi aceito, que é a razão de a
 * tabela existir. Herdar {@link JpaRepository} expõe {@code delete*} tecnicamente; usá-los sobre
 * esta entidade é violação de contrato.
 */
@Repository
public interface UsuarioLgpdConsentRepository extends JpaRepository<UsuarioLgpdConsent, UUID> {

    /**
     * Existe aceite deste usuário, neste tenant, para exatamente estas versões?
     *
     * <p>É a derivação de {@code lgpdConsentGranted} e a decisão da guarda 6 do
     * {@code LgpdConsentInterceptor}. Filtra por tenant de propósito: {@code tenant_id} não tem FK,
     * então o escopo é responsabilidade da query.
     */
    boolean existsByUsuario_IdAndTenantIdAndPolicyVersionAndTermsVersion(
            UUID usuarioId, UUID tenantId, String policyVersion, String termsVersion);

    /**
     * Último aceite do usuário no tenant, para exibição do histórico
     * (consumido por {@code add-coach-settings-page}).
     */
    Optional<UsuarioLgpdConsent> findTopByUsuario_IdAndTenantIdOrderByConsentedAtDesc(
            UUID usuarioId, UUID tenantId);
}
