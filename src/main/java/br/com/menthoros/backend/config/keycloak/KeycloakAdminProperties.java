package br.com.menthoros.backend.config.keycloak;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Propriedades de acesso ao Keycloak Admin REST API.
 *
 * <p>Vinculadas ao prefixo {@code keycloak.admin} no {@code application.yml}.
 * Registrada como {@code @Component} para garantir o bean independentemente de
 * {@code @ConfigurationPropertiesScan}.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "keycloak.admin")
public class KeycloakAdminProperties {

    private String serverUrl;
    private String realm;
    private String tokenRealm;
    private String clientId;
    private String username;
    private String password;
}
