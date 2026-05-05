package br.com.menthoros.backend.config;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.actuate.health.CompositeHealth;
import org.springframework.boot.actuate.health.HealthEndpoint;

@SpringBootTest(properties = {
    "management.endpoints.web.exposure.include=health",
    "management.endpoint.health.show-components=always",
    "spring.datasource.url=jdbc:h2:mem:healthdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class HealthConfigTest {

    @Autowired
    private HealthEndpoint healthEndpoint;

    @Test
    void should_have_strava_health_indicator() {
        var health = (CompositeHealth) healthEndpoint.health();
        assertThat(health.getComponents())
            .containsKey("strava");
    }

    @Test
    void should_have_cache_health_indicator() {
        var health = (CompositeHealth) healthEndpoint.health();
        assertThat(health.getComponents())
            .containsKey("cache");
    }
}
