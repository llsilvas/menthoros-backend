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
public class GarminWebClientConfig {

    static final int MAX_IN_MEMORY_BYTES = 16 * 1024 * 1024;

    private final GarminProperties properties;

    /**
     * {@code baseUrl} é a API ({@code apis.garmin.com}); o token endpoint vive em outro host
     * ({@code diauth.garmin.com}) e o client o chama com URI absoluta, que ignora o baseUrl.
     */
    @Bean("garminWebClient")
    public WebClient garminWebClient() {
        // connect 5s / response 10s — referência Keycloak; nunca bloquear indefinidamente
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .responseTimeout(Duration.ofSeconds(10));

        return WebClient.builder()
                .baseUrl(properties.getApiBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("Accept", "application/json")
                // Padrão de 256 KiB é menor que um FIT de corrida; estourar depois de consumir o link perde o arquivo (410).
                .codecs(c -> c.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_BYTES))
                .build();
    }
}
