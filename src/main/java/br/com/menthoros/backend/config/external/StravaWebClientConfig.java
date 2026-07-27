package br.com.menthoros.backend.config.external;

import io.netty.channel.ChannelOption;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
@RequiredArgsConstructor
public class StravaWebClientConfig {

    private final StravaProperties stravaProperties;

    @Bean("stravaWebClient")
    public WebClient stravaWebClient() {
        // connect 5s / response 10s — referência Keycloak; nunca bloquear indefinidamente.
        // Em código e não no yml: todos os call sites são endpoints pequenos
        // (/activities/{id}, laps, listagem) e ninguém tuna esse valor por ambiente.
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .responseTimeout(Duration.ofSeconds(10));

        return WebClient.builder()
                .baseUrl(stravaProperties.getApiBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("Accept", "application/json")
                .build();
    }
}
