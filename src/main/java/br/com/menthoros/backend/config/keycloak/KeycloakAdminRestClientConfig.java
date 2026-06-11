package br.com.menthoros.backend.config.keycloak;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class KeycloakAdminRestClientConfig {

    @Bean
    public RestClient keycloakAdminRestClient(RestClient.Builder builder, KeycloakAdminProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(10).toMillis());
        return builder.baseUrl(props.getServerUrl()).requestFactory(factory).build();
    }
}
