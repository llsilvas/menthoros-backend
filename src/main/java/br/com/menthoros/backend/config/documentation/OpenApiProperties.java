package br.com.menthoros.backend.config.documentation;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

@Getter
@Setter
@Validated
@Configuration
@ConfigurationProperties(prefix = "app.openapi")
public class OpenApiProperties {

    @NotBlank(message = "title cannot be blank")
    private String title = "Menthoros API";

    @NotBlank(message = "description cannot be blank")
    private String description = "API para gerenciar atletas, treinos e planejamento";

    private String contactName = "Menthoros Team";
    private String contactEmail = "contact@menthoros.com";

    @NotBlank(message = "environment cannot be blank")
    private String environment = "dev";
}
