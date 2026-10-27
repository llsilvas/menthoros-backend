package br.com.menthoros.backend.config.external;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class GarminPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(GarminProperties.class)
    static class TestConfig {}

    @Test
    @DisplayName("sem nenhuma var o contexto sobe, habilitado() e configurado() são false")
    void semVarsSobeDesligado() {
        new ApplicationContextRunner()
                .withUserConfiguration(TestConfig.class)
                .run(ctx -> {
                    GarminProperties props = ctx.getBean(GarminProperties.class);
                    assertThat(props.habilitado()).isFalse();
                    assertThat(props.configurado()).isFalse();
                });
    }

    @Test
    @DisplayName("configurado() exige client-id e client-secret não vazios; habilitado é independente")
    void configuradoExigeIdESecret() {
        new ApplicationContextRunner()
                .withUserConfiguration(TestConfig.class)
                .withPropertyValues("app.garmin.habilitado=true", "app.garmin.client-id=abc",
                        "app.garmin.client-secret= ")
                .run(ctx -> {
                    GarminProperties props = ctx.getBean(GarminProperties.class);
                    assertThat(props.habilitado()).isTrue();
                    assertThat(props.configurado()).isFalse();
                });

        new ApplicationContextRunner()
                .withUserConfiguration(TestConfig.class)
                .withPropertyValues("app.garmin.client-id=abc", "app.garmin.client-secret=xyz")
                .run(ctx -> {
                    GarminProperties props = ctx.getBean(GarminProperties.class);
                    assertThat(props.habilitado()).isFalse();
                    assertThat(props.configurado()).isTrue();
                });
    }

    @Test
    @DisplayName("webhookConfigurado() exige segredo de pelo menos 32 caracteres")
    void webhookExigeSegredoLongo() {
        GarminProperties props = new GarminProperties();
        assertThat(props.webhookConfigurado()).isFalse();
        props.setWebhookSecret("curto");
        assertThat(props.webhookConfigurado()).isFalse();
        props.setWebhookSecret("x".repeat(GarminProperties.WEBHOOK_SECRET_MIN - 1));
        assertThat(props.webhookConfigurado()).isFalse();
        props.setWebhookSecret("x".repeat(GarminProperties.WEBHOOK_SECRET_MIN));
        assertThat(props.webhookConfigurado()).isTrue();
    }
}
