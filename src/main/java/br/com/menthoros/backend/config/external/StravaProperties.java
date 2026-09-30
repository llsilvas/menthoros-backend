package br.com.menthoros.backend.config.external;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.strava")
public class StravaProperties {

    private String clientId;
    private String clientSecret;
    private String redirectUri;
    private String authorizationUri;
    private String tokenUri;
    private String apiBaseUrl;
    private String webhookVerifyToken;
    private int syncDaysBack = 90;
    /**
     * Quanto o pull relista para trás de {@code pull_cursor}: cobre upload tardio com data anterior ao
     * cursor. Upload com mais atraso que isso fica para o webhook {@code create}.
     */
    private int syncOverlapDays = 7;
}
