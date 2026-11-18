package br.com.menthoros.backend.config.external;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guard de modelos retirados (migrate-openai-retired-models, design D5 / CA6): a OpenAI desliga
 * gpt-4o, gpt-4o-mini, gpt-4.1 e gpt-4.1-mini em 2027-01-14. Lê o {@code application.yml} real — não um
 * YAML de teste — e falha se alguma rota de {@code app.llm.routing} ou o default
 * {@code spring.ai.openai.chat.options.model} apontar para um deles ou para um snapshot datado.
 */
@DisplayName("Guard de modelos retirados")
class RetiredModelsGuardTest {

    /** Nome exato ou snapshot datado ({@code gpt-4o-2024-08-06}); não pega {@code gpt-4.1-nano}. */
    private static final Pattern RETIRADO =
            Pattern.compile("^(gpt-4o|gpt-4o-mini|gpt-4\\.1|gpt-4\\.1-mini)(-\\d{4}-\\d{2}-\\d{2})?$");

    @TestConfiguration
    @EnableConfigurationProperties(LlmRoutingProperties.class)
    static class TestConfig {}

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(TestConfig.class);

    @Test
    @org.junit.jupiter.api.Disabled("ROLLBACK para gpt-4o (migrate-openai-retired-models, design D7): exceção deliberada e "
            + "temporária — o gpt-4o é desligado em 2027-01-14; reverter este PR antes disso")
    @DisplayName("application.yml real não usa modelo retirado em nenhuma rota nem no default")
    void configuracaoRealSemModeloRetirado() {
        runner.run(ctx -> assertThat(retirados(ctx)).isEmpty());
    }

    @Test
    @DisplayName("pega uma rota apontada para gpt-4o e o default num snapshot datado")
    void pegaRotaRetirada() {
        // Rota sem reasoning-effort no YAML: com esforço, a própria validação de boot (LlmRoutingProperties)
        // já recusa o gpt-4o e o contexto nem sobe — o guard é a segunda linha.
        runner.withPropertyValues("app.llm.routing.standard.model=gpt-4o",
                        "spring.ai.openai.chat.options.model=gpt-4o-mini-2024-07-18")
                .run(ctx -> assertThat(retirados(ctx)).contains(
                        "app.llm.routing.standard=gpt-4o", "spring.ai.openai.chat.options.model=gpt-4o-mini-2024-07-18"));
    }

    @Test
    @DisplayName("não confunde modelo que só compartilha o prefixo (gpt-4.1-nano, gpt-4o-audio)")
    void naoPegaPrefixoParecido() {
        assertThat(RETIRADO.matcher("gpt-4.1-nano").matches()).isFalse();
        assertThat(RETIRADO.matcher("gpt-4o-audio-preview").matches()).isFalse();
        assertThat(RETIRADO.matcher("gpt-6-luna").matches()).isFalse();
    }

    private static List<String> retirados(ApplicationContext ctx) {
        List<String> achados = new ArrayList<>();
        LlmRoutingProperties props = ctx.getBean(LlmRoutingProperties.class);
        var rotas = java.util.Map.of("simple", props.getSimple(), "standard", props.getStandard(),
                "complex", props.getComplex(), "expert", props.getExpert(), "plano", props.getPlano());
        rotas.forEach((nome, rota) -> {
            if (rota != null && rota.getModel() != null && RETIRADO.matcher(rota.getModel()).matches()) {
                achados.add("app.llm.routing." + nome + "=" + rota.getModel());
            }
        });
        String padrao = ctx.getEnvironment().getProperty("spring.ai.openai.chat.options.model");
        if (padrao != null && RETIRADO.matcher(padrao).matches()) {
            achados.add("spring.ai.openai.chat.options.model=" + padrao);
        }
        return achados;
    }
}
