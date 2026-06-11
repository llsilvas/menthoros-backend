package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.services.KeycloakOrganizationGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Implementação provisória do gateway de Keycloak Organizations.
 *
 * <p>A integração real (Keycloak Admin Client + feature Organizations + convites) depende da
 * configuração do realm {@code menthoros-app} — habilitar Organizations, attribute mapper que
 * projeta {@code tenant_id} no token, e o client role {@code ATLETA} — e de um ambiente Keycloak
 * provisionado. Ver o plano {@code add-assessoria-onboarding} (Fase 5) e
 * {@code docs/add-assessoria-onboarding-keycloak-runbook.md}.
 *
 * <p>Enquanto essa infra não é provida, este bean existe apenas para que o contexto Spring suba e
 * os testes de carga de contexto passem. Qualquer invocação <strong>falha explicitamente</strong>
 * (nunca retorna um id sintético) para não corromper dados com assessorias sem Organization real.
 *
 * <p><strong>Idempotent:</strong> N/A — sempre lança.
 * <p><strong>Side Effects:</strong> NONE (lança antes de qualquer efeito).
 * <p><strong>Tenant-aware:</strong> N/A.
 */
@Slf4j
@Service
public class KeycloakOrganizationGatewayImpl implements KeycloakOrganizationGateway {

    private static final String PENDENTE =
            "Integração Keycloak Organizations pendente (Fase 5 do add-assessoria-onboarding): "
                    + "configure o realm (Organizations + attribute mapper tenant_id + role ATLETA) "
                    + "e implemente o adapter via keycloak-admin-client.";

    @Override
    public String criarOrganization(String nome, String dominio, UUID tenantId) {
        log.error("criarOrganization invocado sem integração Keycloak provisionada: nome={}, dominio={}, tenantId={}",
                nome, dominio, tenantId);
        throw new UnsupportedOperationException(PENDENTE);
    }

    @Override
    public void enviarConviteAtleta(String keycloakOrganizationId, String email, UUID atletaId) {
        log.error("enviarConviteAtleta invocado sem integração Keycloak provisionada: org={}, email={}, atletaId={}",
                keycloakOrganizationId, email, atletaId);
        throw new UnsupportedOperationException(PENDENTE);
    }
}
