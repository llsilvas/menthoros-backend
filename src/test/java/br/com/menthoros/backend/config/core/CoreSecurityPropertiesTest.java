package br.com.menthoros.backend.config.core;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:securitypropstest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@TestPropertySource(properties = {
    "app.security.public-paths[0]=/api/public/**",
    "app.security.public-paths[1]=/swagger-ui/**",
    "app.security.public-paths[2]=/actuator/health",
    "app.security.strava-paths[0]=/api/v1/strava/webhook",
    "app.security.strava-paths[1]=/api/v1/strava/callback"
})
class CoreSecurityPropertiesTest {

    @Autowired
    private CoreSecurityProperties props;

    @Test
    void should_load_public_paths() {
        assertThat(props.getPublicPaths())
            .contains("/api/public/**", "/swagger-ui/**");
    }

    @Test
    void should_load_strava_paths() {
        assertThat(props.getStravaPaths())
            .contains("/api/v1/strava/webhook", "/api/v1/strava/callback");
    }

    @Test
    void should_have_default_health_check_path() {
        assertThat(props.getPublicPaths())
            .contains("/actuator/health");
    }
}
