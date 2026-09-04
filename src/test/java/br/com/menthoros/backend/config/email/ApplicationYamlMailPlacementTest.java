package br.com.menthoros.backend.config.email;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O bloco {@code mail:} precisa estar sob {@code spring:} — indentado sob outro top-level
 * (ex.: {@code logging:}) o Boot não cria o {@link org.springframework.mail.javamail.JavaMailSender}
 * e o startup em cloud/dev cai com "required a bean ... that could not be found"
 * (incidente Railway de 2026-08-31).
 */
@DisplayName("application-{cloud,dev}.yml: bloco mail dentro de spring")
class ApplicationYamlMailPlacementTest {

    @SuppressWarnings("unchecked")
    @ParameterizedTest(name = "application-{0}.yml")
    @ValueSource(strings = {"cloud", "dev"})
    void mailFicaSobSpring(String profile) throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/application-" + profile + ".yml")) {
            assertThat(in).as("application-%s.yml no classpath", profile).isNotNull();
            Map<String, Object> doc = new Yaml().load(in);

            Map<String, Object> spring = (Map<String, Object>) doc.get("spring");
            assertThat(spring).as("bloco spring").isNotNull();
            Map<String, Object> mail = (Map<String, Object>) spring.get("mail");
            assertThat(mail).as("spring.mail").isNotNull();
            assertThat(mail).as("spring.mail.host").containsKey("host");

            Map<String, Object> logging = (Map<String, Object>) doc.get("logging");
            if (logging != null) {
                assertThat(logging).as("mail dentro de logging (bug do incidente)").doesNotContainKey("mail");
            }
        }
    }
}
