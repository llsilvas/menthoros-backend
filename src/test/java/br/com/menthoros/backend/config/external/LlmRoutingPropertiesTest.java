package br.com.menthoros.backend.config.external;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class LlmRoutingPropertiesTest {

    private static final String[] PROPRIEDADES_COMPLETAS = {
            "app.llm.routing.simple.model=gpt-4o-mini",
            "app.llm.routing.simple.temperature=0.3",
            "app.llm.routing.simple.max-tokens=1000",
            "app.llm.routing.standard.model=claude-haiku-4-5-20251001",
            "app.llm.routing.standard.temperature=0.5",
            "app.llm.routing.standard.max-tokens=2000",
            "app.llm.routing.complex.model=claude-sonnet-4-6",
            "app.llm.routing.complex.temperature=0.7",
            "app.llm.routing.complex.max-tokens=4000",
            "app.llm.routing.expert.model=gpt-4o",
            "app.llm.routing.expert.temperature=0.2",
            "app.llm.routing.expert.max-tokens=8000",
            "app.llm.routing.plano.model=gpt-4o",
            "app.llm.routing.plano.temperature=0.2",
            "app.llm.routing.plano.max-tokens=12000"
    };

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    // @TestConfiguration (e não @Configuration): classes aninhadas @Configuration em
    // testes entram no component scan dos @SpringBootTest e duplicariam o bean de
    // properties (registrar + auto-registro da própria classe) no contexto da aplicação.
    @TestConfiguration
    @EnableConfigurationProperties(LlmRoutingProperties.class)
    static class TestConfig {}

    @Nested
    @DisplayName("binding")
    class Binding {

        @Test
        @DisplayName("carrega as 5 rotas com model, temperature e maxTokens")
        void carregaTodasAsRotas() {
            contextRunner.withPropertyValues(PROPRIEDADES_COMPLETAS).run(ctx -> {
                LlmRoutingProperties props = ctx.getBean(LlmRoutingProperties.class);

                assertThat(props.getSimple().getModel()).isEqualTo("gpt-4o-mini");
                assertThat(props.getSimple().getTemperature()).isEqualTo(0.3);
                assertThat(props.getSimple().getMaxTokens()).isEqualTo(1000);

                assertThat(props.getStandard().getModel()).isEqualTo("claude-haiku-4-5-20251001");
                assertThat(props.getStandard().getTemperature()).isEqualTo(0.5);
                assertThat(props.getStandard().getMaxTokens()).isEqualTo(2000);

                assertThat(props.getComplex().getModel()).isEqualTo("claude-sonnet-4-6");
                assertThat(props.getComplex().getTemperature()).isEqualTo(0.7);
                assertThat(props.getComplex().getMaxTokens()).isEqualTo(4000);

                assertThat(props.getExpert().getModel()).isEqualTo("gpt-4o");
                assertThat(props.getExpert().getTemperature()).isEqualTo(0.2);
                assertThat(props.getExpert().getMaxTokens()).isEqualTo(8000);

                assertThat(props.getPlano().getModel()).isEqualTo("gpt-4o");
                assertThat(props.getPlano().getTemperature()).isEqualTo(0.2);
                assertThat(props.getPlano().getMaxTokens()).isEqualTo(12000);
            });
        }

        @Test
        @DisplayName("falha o contexto quando uma rota obrigatória está ausente")
        void falhaSemRotaObrigatoria() {
            String[] semExpert = java.util.Arrays.stream(PROPRIEDADES_COMPLETAS)
                    .filter(p -> !p.startsWith("app.llm.routing.expert"))
                    .toArray(String[]::new);

            contextRunner.withPropertyValues(semExpert).run(ctx ->
                    assertThat(ctx).hasFailed());
        }

        @Test
        @DisplayName("application.yml real define temperatura 0.2 na rota expert (deep-reasoning determinístico)")
        void temperaturaExpertNoYmlReal() {
            new ApplicationContextRunner()
                    .withInitializer(new org.springframework.boot.test.context.ConfigDataApplicationContextInitializer())
                    .withUserConfiguration(TestConfig.class)
                    .run(ctx -> {
                        LlmRoutingProperties props = ctx.getBean(LlmRoutingProperties.class);
                        assertThat(props.getExpert().getModel()).isEqualTo("gpt-4o");
                        assertThat(props.getExpert().getTemperature()).isEqualTo(0.2);
                    });
        }

        @Test
        @DisplayName("falha o contexto quando o model de uma rota está em branco")
        void falhaComModelEmBranco() {
            String[] modelVazio = java.util.Arrays.stream(PROPRIEDADES_COMPLETAS)
                    .map(p -> p.equals("app.llm.routing.expert.model=gpt-4o")
                            ? "app.llm.routing.expert.model=" : p)
                    .toArray(String[]::new);

            contextRunner.withPropertyValues(modelVazio).run(ctx ->
                    assertThat(ctx).hasFailed());
        }
    }
}
