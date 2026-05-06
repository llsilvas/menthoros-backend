package br.com.menthoros.backend.config.documentation;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class OpenApiConfig {

    private final OpenApiProperties openApiProperties;

    @Value("${build.version:1.0.0}")
    private String projectVersion;

    @Bean
    public OpenAPI menthorosOpenAPI() {
        Contact contact = new Contact()
            .name(openApiProperties.getContactName())
            .email(openApiProperties.getContactEmail());

        Info info = new Info()
            .title(openApiProperties.getTitle())
            .version(projectVersion)
            .description(openApiProperties.getDescription() +
                " (Environment: " + openApiProperties.getEnvironment() + ")")
            .contact(contact);

        return new OpenAPI()
            .info(info);
    }
}
