package br.com.menthoros.backend.config.core;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotEmpty;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Validated
@Configuration
@ConfigurationProperties(prefix = "app.security")
public class CoreSecurityProperties {

    @NotEmpty(message = "publicPaths cannot be empty")
    private List<String> publicPaths = new ArrayList<>(List.of(
        "/api/public/**",
        "/swagger-ui/**",
        "/api-docs/**",
        "/v3/api-docs/**",
        "/actuator/health"
    ));

    @NotEmpty(message = "stravaPaths cannot be empty")
    private List<String> stravaPaths = new ArrayList<>(List.of(
        "/api/v1/strava/webhook",
        "/api/v1/strava/callback"
    ));
}
