package br.com.menthoros.backend.config.core;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class CoreSecurityPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(TestConfig.class)
        .withPropertyValues(
            "app.security.public-paths[0]=/api/public/**",
            "app.security.public-paths[1]=/swagger-ui/**",
            "app.security.public-paths[2]=/actuator/health",
            "app.security.strava-paths[0]=/api/v1/strava/webhook",
            "app.security.strava-paths[1]=/api/v1/strava/callback"
        );

    @Configuration
    @EnableConfigurationProperties(CoreSecurityProperties.class)
    static class TestConfig {}

    @Test
    void should_load_public_paths() {
        contextRunner.run(ctx -> {
            CoreSecurityProperties props = ctx.getBean(CoreSecurityProperties.class);
            assertThat(props.getPublicPaths())
                .contains("/api/public/**", "/swagger-ui/**");
        });
    }

    @Test
    void should_load_strava_paths() {
        contextRunner.run(ctx -> {
            CoreSecurityProperties props = ctx.getBean(CoreSecurityProperties.class);
            assertThat(props.getStravaPaths())
                .contains("/api/v1/strava/webhook", "/api/v1/strava/callback");
        });
    }

    @Test
    void should_have_default_health_check_path() {
        contextRunner.run(ctx -> {
            CoreSecurityProperties props = ctx.getBean(CoreSecurityProperties.class);
            assertThat(props.getPublicPaths())
                .contains("/actuator/health");
        });
    }
}
