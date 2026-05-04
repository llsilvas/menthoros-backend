package br.com.menthoros.backend.config.documentation;

import static org.assertj.core.api.Assertions.*;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "project.version=1.0.0",
    "app.openapi.environment=test",
    "app.openapi.title=Test API",
    "app.openapi.description=Test Description",
    "app.openapi.contact-name=Menthoros Team",
    "app.openapi.contact-email=contact@menthoros.com"
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
            .contains("test");
    }

    @Test
    void should_have_contact_information() {
        assertThat(openAPI.getInfo().getContact()).isNotNull();
        assertThat(openAPI.getInfo().getContact().getName())
            .isEqualTo("Menthoros Team");
    }
}
