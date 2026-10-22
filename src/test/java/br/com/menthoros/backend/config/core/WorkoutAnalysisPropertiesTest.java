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

    @Nested
    @DisplayName("verdict")
    class Verdict {

        @Test
        @DisplayName("sem a chave, vale toleranciaPct=15.0 e deltaRpe=2")
        void defaults() {
            contextRunner.run(ctx -> {
                WorkoutAnalysisProperties props = ctx.getBean(WorkoutAnalysisProperties.class);
                assertThat(props.getVerdict().getToleranciaPct()).isEqualTo(15.0);
                assertThat(props.getVerdict().getDeltaRpe()).isEqualTo(2);
            });
        }

        @Test
        @DisplayName("carrega toleranciaPct e deltaRpe quando informados")
        void carregaValoresInformados() {
            contextRunner.withPropertyValues(
                            "app.workout-analysis.verdict.tolerancia-pct=10.0",
                            "app.workout-analysis.verdict.delta-rpe=3")
                    .run(ctx -> {
                        WorkoutAnalysisProperties props = ctx.getBean(WorkoutAnalysisProperties.class);
                        assertThat(props.getVerdict().getToleranciaPct()).isEqualTo(10.0);
                        assertThat(props.getVerdict().getDeltaRpe()).isEqualTo(3);
                    });
        }

        @Test
        @DisplayName("falha o contexto com toleranciaPct negativo")
        void falhaComToleranciaNegativa() {
            contextRunner.withPropertyValues("app.workout-analysis.verdict.tolerancia-pct=-1")
                    .run(ctx -> assertThat(ctx).hasFailed());
        }

        @Test
        @DisplayName("falha o contexto com deltaRpe negativo")
        void falhaComDeltaRpeNegativo() {
            contextRunner.withPropertyValues("app.workout-analysis.verdict.delta-rpe=-1")
                    .run(ctx -> assertThat(ctx).hasFailed());
        }

        @Test
        @DisplayName("aceita toleranciaPct=0 e deltaRpe=0")
        void aceitaZero() {
            contextRunner.withPropertyValues(
                            "app.workout-analysis.verdict.tolerancia-pct=0",
                            "app.workout-analysis.verdict.delta-rpe=0")
                    .run(ctx -> assertThat(ctx).hasNotFailed());
        }
    }
}
