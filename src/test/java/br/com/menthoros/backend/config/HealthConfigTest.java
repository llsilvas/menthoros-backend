package br.com.menthoros.backend.config;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.actuate.health.CompositeHealth;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;

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
        var health = healthEndpoint.health();
        assertThat(health).isInstanceOf(CompositeHealth.class);
        var composite = (CompositeHealth) health;
        assertThat(composite.getComponents()).containsKey("strava");
        assertThat(composite.getComponents().get("strava").getStatus())
            .isEqualTo(Status.UP);
    }

    @Test
    void should_have_cache_health_indicator() {
        var health = healthEndpoint.health();
        assertThat(health).isInstanceOf(CompositeHealth.class);
        var composite = (CompositeHealth) health;
        assertThat(composite.getComponents()).containsKey("cache");
        assertThat(composite.getComponents().get("cache").getStatus())
            .isEqualTo(Status.UP);
    }
}
