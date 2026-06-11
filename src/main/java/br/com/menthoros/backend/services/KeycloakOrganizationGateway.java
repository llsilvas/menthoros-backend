package br.com.menthoros.backend.services;

import java.util.UUID;

/**
 * Abstração da integração com o Keycloak para Organizations e convites.
 * O service de domínio depende desta interface (mockável em testes);
 * o adapter real usa o Keycloak Admin Client (infra).
 */
public interface KeycloakOrganizationGateway {

    /**
     * Cria uma Organization no realm para a assessoria e injeta o atributo tenant_id.
     * @return o id da Organization criada no Keycloak
     */
    String criarOrganization(String nome, String dominio, UUID tenantId);

    /**
     * Gera/reenvia um convite de atleta vinculado à Organization da assessoria.
     */
    void enviarConviteAtleta(String keycloakOrganizationId, String email, UUID atletaId);
}
