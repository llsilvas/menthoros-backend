package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.config.keycloak.KeycloakAdminProperties;
import br.com.menthoros.backend.exception.KeycloakIntegrationException;
import br.com.menthoros.backend.services.KeycloakOrganizationGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Adapter real do gateway de Keycloak Organizations, via Keycloak Admin REST API
 * (Spring {@link RestClient}). Cada operação obtém um token de admin (password grant
 * no realm de token) e então executa a chamada autenticada no realm de aplicação.
 *
 * <p>Segredos (senha de admin, tokens) nunca são logados.
 */
@Slf4j
@Service
public class KeycloakOrganizationGatewayImpl implements KeycloakOrganizationGateway {

    private final RestClient restClient;
    private final KeycloakAdminProperties props;

    public KeycloakOrganizationGatewayImpl(RestClient.Builder builder, KeycloakAdminProperties props) {
        this.props = props;
        this.restClient = builder.baseUrl(props.getServerUrl()).build();
    }

    /**
     * Cria uma Organization no realm da aplicação e injeta o atributo {@code tenant_id}.
     *
     * <p><strong>Idempotent:</strong> NO — cada chamada cria uma nova Organization.
     * <p><strong>Side Effects:</strong> External API (Keycloak) — cria Organization.
     * <p><strong>Tenant-aware:</strong> N/A — recebe o {@code tenantId} explicitamente.
     *
     * @param nome nome da assessoria
     * @param dominio slug ({@code ^[a-z0-9-]+$}), usado como alias e domínio
     * @param tenantId tenant a ser gravado no atributo {@code tenant_id}
     * @return o id da Organization criada no Keycloak
     * @throws KeycloakIntegrationException em falha de token, resposta não-2xx ou Location ausente
     */
    @Override
    public String criarOrganization(String nome, String dominio, UUID tenantId) {
        log.info("Criando Organization no Keycloak: nome={}, dominio={}, tenantId={}", nome, dominio, tenantId);
        String token = obterTokenAdmin();

        Map<String, Object> body = Map.of(
                "name", nome,
                "alias", dominio,
                "domains", List.of(Map.of("name", dominio, "verified", false)),
                "attributes", Map.of("tenant_id", List.of(tenantId.toString()))
        );

        try {
            ResponseEntity<Void> response = restClient.post()
                    .uri("/admin/realms/{realm}/organizations", props.getRealm())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            URI location = response.getHeaders().getLocation();
            if (location == null) {
                throw new KeycloakIntegrationException(
                        "Keycloak não retornou header Location ao criar Organization (dominio=" + dominio + ")");
            }
            String orgId = extrairIdDaLocation(location.toString());
            log.info("Organization criada no Keycloak: orgId={}, dominio={}", orgId, dominio);
            return orgId;
        } catch (KeycloakIntegrationException e) {
            throw e;
        } catch (Exception e) {
            throw new KeycloakIntegrationException(
                    "Falha ao criar Organization no Keycloak (dominio=" + dominio + ")", e);
        }
    }

    /**
     * Envia (ou reenvia) um convite de atleta vinculado à Organization da assessoria.
     *
     * <p><strong>Idempotent:</strong> NO — reenvia o convite a cada chamada.
     * <p><strong>Side Effects:</strong> External API (Keycloak) — dispara convite por email.
     * <p><strong>Tenant-aware:</strong> N/A — escopo definido pela Organization.
     *
     * @param keycloakOrganizationId id da Organization no Keycloak
     * @param email email do atleta convidado
     * @param atletaId id do atleta (apenas para correlação em log)
     * @throws KeycloakIntegrationException em falha de token ou resposta não-2xx
     */
    @Override
    public void enviarConviteAtleta(String keycloakOrganizationId, String email, UUID atletaId) {
        log.info("Enviando convite de atleta via Keycloak: orgId={}, atletaId={}",
                keycloakOrganizationId, atletaId);
        String token = obterTokenAdmin();

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("email", email);

        try {
            restClient.post()
                    .uri("/admin/realms/{realm}/organizations/{orgId}/members/invite-user",
                            props.getRealm(), keycloakOrganizationId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Convite de atleta enviado: orgId={}, atletaId={}", keycloakOrganizationId, atletaId);
        } catch (Exception e) {
            throw new KeycloakIntegrationException(
                    "Falha ao enviar convite de atleta no Keycloak (orgId=" + keycloakOrganizationId + ")", e);
        }
    }

    private String obterTokenAdmin() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", props.getClientId());
        form.add("username", props.getUsername());
        form.add("password", props.getPassword());

        try {
            TokenResponse token = restClient.post()
                    .uri("/realms/{tokenRealm}/protocol/openid-connect/token", props.getTokenRealm())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);

            if (token == null || token.accessToken() == null || token.accessToken().isBlank()) {
                throw new KeycloakIntegrationException("Keycloak retornou token de admin vazio");
            }
            return token.accessToken();
        } catch (KeycloakIntegrationException e) {
            throw e;
        } catch (Exception e) {
            throw new KeycloakIntegrationException("Falha ao obter token de admin no Keycloak", e);
        }
    }

    private String extrairIdDaLocation(String location) {
        int idx = location.lastIndexOf('/');
        if (idx < 0 || idx == location.length() - 1) {
            throw new KeycloakIntegrationException("Location inválido retornado pelo Keycloak: " + location);
        }
        return location.substring(idx + 1);
    }

    private record TokenResponse(
            @com.fasterxml.jackson.annotation.JsonProperty("access_token") String accessToken) {
    }
}
