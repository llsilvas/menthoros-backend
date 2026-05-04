package br.com.menthoros.backend.config.core;

import static org.assertj.core.api.Assertions.*;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Set;

class CachePropertiesTest {

    private static Validator validator;

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(TestConfig.class)
        .withPropertyValues(
            "app.cache.default-ttl=PT30M",
            "app.cache.maximum-size=1000"
        );

    @Configuration
    @EnableConfigurationProperties(CacheProperties.class)
    static class TestConfig {}

    @BeforeAll
    static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void should_load_cache_properties() {
        contextRunner.run(ctx -> {
            CacheProperties props = ctx.getBean(CacheProperties.class);
            assertThat(props.getDefaultTtl()).isEqualTo(Duration.ofMinutes(30));
            assertThat(props.getMaximumSize()).isEqualTo(1000L);
        });
    }

    @Test
    void should_validate_ttl_is_positive() {
        CacheProperties invalid = new CacheProperties();
        invalid.setDefaultTtl(Duration.ZERO);

        Set<ConstraintViolation<CacheProperties>> violations =
            validator.validate(invalid);

        assertThat(violations)
            .isNotEmpty()
            .anyMatch(v -> v.getMessage().contains("positive"));
    }

    @Test
    void should_validate_negative_ttl() {
        CacheProperties invalid = new CacheProperties();
        invalid.setDefaultTtl(Duration.ofMinutes(-1));

        Set<ConstraintViolation<CacheProperties>> violations =
            validator.validate(invalid);

        assertThat(violations)
            .isNotEmpty()
            .anyMatch(v -> v.getMessage().contains("positive"));
    }

    @Test
    void should_validate_maximum_size_minimum() {
        CacheProperties invalid = new CacheProperties();
        invalid.setMaximumSize(5);

        Set<ConstraintViolation<CacheProperties>> violations =
            validator.validate(invalid);

        assertThat(violations).isNotEmpty();
    }

    @Test
    void should_pass_validation_with_valid_values() {
        CacheProperties valid = new CacheProperties();
        valid.setDefaultTtl(Duration.ofMinutes(15));
        valid.setMaximumSize(100);

        Set<ConstraintViolation<CacheProperties>> violations =
            validator.validate(valid);

        assertThat(violations).isEmpty();
    }
}
