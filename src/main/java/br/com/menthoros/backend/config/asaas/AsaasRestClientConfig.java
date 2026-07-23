package br.com.menthoros.backend.config.asaas;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * {@link RestClient} da API do Asaas, com timeouts de connect/read obrigatórios
 * ("External Call Resilience" do CLAUDE.md — nenhuma chamada externa bloqueia
 * indefinidamente). O header de autenticação {@code access_token} e o
 * {@code User-Agent} obrigatório são default headers estáticos.
 */
@Configuration
public class AsaasRestClientConfig {

    @Bean
    public RestClient asaasRestClient(RestClient.Builder builder, AsaasProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(10).toMillis());
        return builder
                .baseUrl(props.getBaseUrl())
                .requestFactory(factory)
                .defaultHeader("access_token", props.getApiKey())
                .defaultHeader("User-Agent", props.getUserAgent())
                .build();
    }
}
