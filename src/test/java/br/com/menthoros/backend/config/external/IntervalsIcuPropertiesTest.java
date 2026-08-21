package br.com.menthoros.backend.config.external;

import static org.assertj.core.api.Assertions.*;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class IntervalsIcuPropertiesTest {

    private static final String[] PROPRIEDADES_COMPLETAS = {
            "app.intervals-icu.base-url=https://intervals.icu",
            "app.intervals-icu.client-id=663",
            "app.intervals-icu.client-secret=segredo-de-teste",
            "app.intervals-icu.redirect-uri=http://localhost:8099/api/v1/integracoes/intervals-icu/callback",
            "app.intervals-icu.authorization-uri=https://intervals.icu/oauth/authorize",
            "app.intervals-icu.token-uri=https://intervals.icu/api/oauth/token",
            "app.intervals-icu.scope=ACTIVITY:READ,CALENDAR:WRITE"
    };

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    // @TestConfiguration (e não @Configuration) pelo mesmo motivo de LlmRoutingPropertiesTest:
    // classes aninhadas @Configuration entram no component scan dos @SpringBootTest.
    @TestConfiguration
    @EnableConfigurationProperties(IntervalsIcuProperties.class)
    static class TestConfig {}

    private static String[] semAPropriedade(String chave) {
        return Arrays.stream(PROPRIEDADES_COMPLETAS)
                .filter(p -> !p.startsWith(chave + "="))
                .toArray(String[]::new);
    }

    private static String[] comValorVazio(String chave) {
        return Arrays.stream(PROPRIEDADES_COMPLETAS)
                .map(p -> p.startsWith(chave + "=") ? chave + "=" : p)
                .toArray(String[]::new);
    }

    @Nested
    @DisplayName("binding")
    class Binding {

        @Test
        @DisplayName("carrega as sete propriedades do fluxo OAuth2")
        void carregaTodasAsPropriedades() {
            contextRunner.withPropertyValues(PROPRIEDADES_COMPLETAS).run(ctx -> {
                IntervalsIcuProperties props = ctx.getBean(IntervalsIcuProperties.class);

                assertThat(props.getBaseUrl()).isEqualTo("https://intervals.icu");
                assertThat(props.getClientId()).isEqualTo("663");
                assertThat(props.getClientSecret()).isEqualTo("segredo-de-teste");
                assertThat(props.getRedirectUri())
                        .isEqualTo("http://localhost:8099/api/v1/integracoes/intervals-icu/callback");
                assertThat(props.getAuthorizationUri()).isEqualTo("https://intervals.icu/oauth/authorize");
                assertThat(props.getTokenUri()).isEqualTo("https://intervals.icu/api/oauth/token");
                assertThat(props.getScope()).isEqualTo("ACTIVITY:READ,CALENDAR:WRITE");
            });
        }
    }

    @Nested
    @DisplayName("validacao")
    class Validacao {

        // D11: o clientSecret é a chave do HMAC que assina o state do callback público.
        // Com chave vazia o state vira forjável para qualquer atletaId — e o fluxo continua
        // funcionando, sem sintoma. Este é o teste que impede alguém de "simplificar" o
        // @NotBlank por achar que é higiene de configuração.
        @Test
        @DisplayName("falha o contexto quando client-secret está vazio (chave do HMAC do state)")
        void falhaComClientSecretVazio() {
            contextRunner.withPropertyValues(comValorVazio("app.intervals-icu.client-secret"))
                    .run(ctx -> assertThat(ctx).hasFailed());
        }

        @Test
        @DisplayName("falha o contexto quando client-secret está ausente")
        void falhaSemClientSecret() {
            contextRunner.withPropertyValues(semAPropriedade("app.intervals-icu.client-secret"))
                    .run(ctx -> assertThat(ctx).hasFailed());
        }

        @Test
        @DisplayName("falha o contexto quando client-id está vazio")
        void falhaComClientIdVazio() {
            contextRunner.withPropertyValues(comValorVazio("app.intervals-icu.client-id"))
                    .run(ctx -> assertThat(ctx).hasFailed());
        }

        @Test
        @DisplayName("falha o contexto quando redirect-uri está vazia")
        void falhaComRedirectUriVazia() {
            contextRunner.withPropertyValues(comValorVazio("app.intervals-icu.redirect-uri"))
                    .run(ctx -> assertThat(ctx).hasFailed());
        }

        @Test
        @DisplayName("falha o contexto quando token-uri está vazia")
        void falhaComTokenUriVazia() {
            contextRunner.withPropertyValues(comValorVazio("app.intervals-icu.token-uri"))
                    .run(ctx -> assertThat(ctx).hasFailed());
        }

        @Test
        @DisplayName("falha o contexto quando authorization-uri está vazia")
        void falhaComAuthorizationUriVazia() {
            contextRunner.withPropertyValues(comValorVazio("app.intervals-icu.authorization-uri"))
                    .run(ctx -> assertThat(ctx).hasFailed());
        }

        @Test
        @DisplayName("falha o contexto quando scope está vazio")
        void falhaComScopeVazio() {
            contextRunner.withPropertyValues(comValorVazio("app.intervals-icu.scope"))
                    .run(ctx -> assertThat(ctx).hasFailed());
        }

        @Test
        @DisplayName("falha o contexto quando base-url está vazia")
        void falhaComBaseUrlVazia() {
            contextRunner.withPropertyValues(comValorVazio("app.intervals-icu.base-url"))
                    .run(ctx -> assertThat(ctx).hasFailed());
        }
    }

    @Nested
    @DisplayName("applicationYmlReal")
    class ApplicationYmlReal {

        // O escopo pedido precisa cobrir as duas operações que já existem em produção:
        // ACTIVITY:READ para a ingestão e CALENDAR:WRITE para o push de treino planejado
        // ao relógio (D4). Um escopo faltando só apareceria na primeira operação real.
        @Test
        @DisplayName("o yml real pede ACTIVITY:READ e CALENDAR:WRITE, e o token-uri tem o /api")
        void contratoDoYmlReal() {
            new ApplicationContextRunner()
                    .withInitializer(new org.springframework.boot.test.context.ConfigDataApplicationContextInitializer())
                    .withUserConfiguration(TestConfig.class)
                    // O yml real deixa os dois vazios de proposito (vem de env var em runtime);
                    // preenchidos aqui so para o contexto subir e o resto ser assertado.
                    .withPropertyValues(
                            "app.intervals-icu.client-id=663",
                            "app.intervals-icu.client-secret=segredo-de-teste")
                    .run(ctx -> {
                        IntervalsIcuProperties props = ctx.getBean(IntervalsIcuProperties.class);

                        assertThat(props.getScope()).contains("ACTIVITY:READ", "CALENDAR:WRITE");
                        // O /api no caminho foi uma das três premissas erradas da spec anterior.
                        assertThat(props.getTokenUri()).isEqualTo("https://intervals.icu/api/oauth/token");
                        assertThat(props.getAuthorizationUri()).isEqualTo("https://intervals.icu/oauth/authorize");
                    });
        }
    }
}
