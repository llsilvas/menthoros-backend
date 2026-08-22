package br.com.menthoros.backend.config.documentation;

import static org.assertj.core.api.Assertions.*;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "build.version=1.0.0",
    "app.openapi.environment=test",
    "app.openapi.title=Test API",
    "app.openapi.description=Test Description",
    "app.openapi.contact-name=Menthoros Team",
    "app.openapi.contact-email=contact@menthoros.com",
    // Sobe o contexto inteiro só para inspecionar o bean OpenAPI — e o contexto inteiro arrasta
    // Flyway e JPA. Sem H2, o datasource default aponta para localhost:5432 e o teste passa apenas
    // onde já existe um Postgres no ar: verde na máquina do dev, vermelho em qualquer runner limpo.
    // Mesmo tratamento de HealthConfigTest e AuditConfigTest.
    "spring.datasource.url=jdbc:h2:mem:openapidb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    // Contexto sem profile herda o application.yml real, que deixa client-id/client-secret
    // vazios (vem de env var em runtime). IntervalsIcuProperties e @NotBlank e sem isto o
    // contexto nao sobe. Ver D11 da change intervals-icu-oauth2-integration.
    "app.intervals-icu.client-id=test-intervals-client-id",
    "app.intervals-icu.client-secret=test-intervals-client-secret"
})
class OpenApiConfigTest {

    @Autowired
    private OpenAPI openAPI;

    @Test
    void should_inject_version_from_project_properties() {
        assertThat(openAPI.getInfo().getVersion())
            .isEqualTo("1.0.0");
    }

    @Test
    void should_include_environment_in_description() {
        assertThat(openAPI.getInfo().getDescription())
            .contains("Environment: test");
    }

    @Test
    void should_have_contact_information() {
        assertThat(openAPI.getInfo().getContact()).isNotNull();
        assertThat(openAPI.getInfo().getContact().getName())
            .isEqualTo("Menthoros Team");
    }
}
