package br.com.menthoros.backend.config.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class WorkoutAnalysisPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @TestConfiguration
    @EnableConfigurationProperties(WorkoutAnalysisProperties.class)
    static class TestConfig {}

    @Nested
    @DisplayName("binding")
    class Binding {

        @Test
        @DisplayName("sem a chave, vale 30 dias de idade máxima")
        void defaultTrintaDias() {
            contextRunner.run(ctx -> {
                WorkoutAnalysisProperties props = ctx.getBean(WorkoutAnalysisProperties.class);
                assertThat(props.getMaxIdadeDias()).isEqualTo(30);
            });
        }

        @Test
        @DisplayName("carrega max-idade-dias quando informado")
        void carregaMaxIdadeDias() {
            contextRunner.withPropertyValues("app.workout-analysis.max-idade-dias=7")
                    .run(ctx -> {
                        WorkoutAnalysisProperties props = ctx.getBean(WorkoutAnalysisProperties.class);
                        assertThat(props.getMaxIdadeDias()).isEqualTo(7);
                    });
        }
    }

    @Nested
    @DisplayName("validacao")
    class Validacao {

        @Test
        @DisplayName("falha o contexto com max-idade-dias=0")
        void falhaComZero() {
            contextRunner.withPropertyValues("app.workout-analysis.max-idade-dias=0")
                    .run(ctx -> assertThat(ctx).hasFailed());
        }

        @Test
        @DisplayName("falha o contexto com max-idade-dias negativo")
        void falhaComNegativo() {
            contextRunner.withPropertyValues("app.workout-analysis.max-idade-dias=-1")
                    .run(ctx -> assertThat(ctx).hasFailed());
        }

        @Test
        @DisplayName("aceita max-idade-dias=1")
        void aceitaUm() {
            contextRunner.withPropertyValues("app.workout-analysis.max-idade-dias=1")
                    .run(ctx -> assertThat(ctx).hasNotFailed());
        }
    }
}
