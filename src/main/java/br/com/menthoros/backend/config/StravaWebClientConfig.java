package br.com.menthoros.backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@RequiredArgsConstructor
public class StravaWebClientConfig {

    private final StravaProperties stravaProperties;

    @Bean("stravaWebClient")
    public WebClient stravaWebClient() {
        return WebClient.builder()
                .baseUrl(stravaProperties.getApiBaseUrl())
                .defaultHeader("Accept", "application/json")
                .build();
    }
}
