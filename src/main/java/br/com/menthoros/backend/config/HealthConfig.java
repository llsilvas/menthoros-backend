package br.com.menthoros.backend.config;

import br.com.menthoros.backend.config.external.StravaProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class HealthConfig {

    private final StravaProperties stravaProperties;
    private final CacheManager cacheManager;

    @Bean
    public HealthIndicator stravaHealthIndicator() {
        return () -> {
            try {
                if (stravaProperties.getApiBaseUrl() == null ||
                    stravaProperties.getApiBaseUrl().isEmpty()) {
                    return Health.down()
                        .withDetail("reason", "Strava API URL not configured")
                        .build();
                }

                return Health.up()
                    .withDetail("service", "strava")
                    .withDetail("baseUrl", stravaProperties.getApiBaseUrl())
                    .build();
            } catch (Exception e) {
                return Health.down()
                    .withDetail("reason", e.getMessage())
                    .build();
            }
        };
    }

    /**
     * Custom cache health indicator that specifically checks the "atletas" cache.
     * Note: This bean overrides Spring Boot's default cacheHealthIndicator to provide
     * application-specific cache health checks beyond the default all-caches probe.
     */
    @Bean
    public HealthIndicator cacheHealthIndicator() {
        return () -> {
            try {
                var cache = cacheManager.getCache("atletas");
                if (cache != null) {
                    return Health.up()
                        .withDetail("caches", cacheManager.getCacheNames())
                        .build();
                } else {
                    return Health.down()
                        .withDetail("reason", "Cache 'atletas' not found")
                        .build();
                }
            } catch (Exception e) {
                return Health.down()
                    .withDetail("reason", e.getMessage())
                    .build();
            }
        };
    }
}
