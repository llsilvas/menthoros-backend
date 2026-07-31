package br.com.menthoros.backend.config.lgpd;

import br.com.menthoros.backend.enums.ConsentEnforcementMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Binding de {@code app.lgpd}.
 *
 * <p>O ponto destes testes é o comportamento em caso de erro, não o caminho feliz: se um valor
 * inválido caísse silenciosamente no default {@code OFF}, o enforcement estaria desligado em
 * produção sem nenhum sinal — exatamente o modo de falha que a flag existe para evitar. Por isso a
 * exigência é <b>falhar no boot</b>.
 */
class LgpdPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(TestConfig.class);

    @Test
    @DisplayName("relaxed binding resolve os três estágios de rollout")
    void resolveOsTresEstagios() {
        runner.withPropertyValues(
                        "app.lgpd.consent-enforcement=off",
                        "app.lgpd.policy-version=2026-06-30",
                        "app.lgpd.terms-version=2026-06-30")
                .run(ctx -> assertThat(ctx.getBean(LgpdProperties.class).getConsentEnforcement())
                        .isEqualTo(ConsentEnforcementMode.OFF));

        runner.withPropertyValues(
                        "app.lgpd.consent-enforcement=report-only",
                        "app.lgpd.policy-version=2026-06-30",
                        "app.lgpd.terms-version=2026-06-30")
                .run(ctx -> assertThat(ctx.getBean(LgpdProperties.class).getConsentEnforcement())
                        .isEqualTo(ConsentEnforcementMode.REPORT_ONLY));

        runner.withPropertyValues(
                        "app.lgpd.consent-enforcement=on",
                        "app.lgpd.policy-version=2026-06-30",
                        "app.lgpd.terms-version=2026-06-30")
                .run(ctx -> assertThat(ctx.getBean(LgpdProperties.class).getConsentEnforcement())
                        .isEqualTo(ConsentEnforcementMode.ON));
    }

    @Test
    @DisplayName("valor desconhecido derruba o contexto — não cai em OFF silencioso")
    void valorDesconhecidoFalha() {
        runner.withPropertyValues(
                        "app.lgpd.consent-enforcement=talvez",
                        "app.lgpd.policy-version=2026-06-30",
                        "app.lgpd.terms-version=2026-06-30")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    @DisplayName("policy-version em branco derruba o contexto")
    void policyVersionEmBrancoFalha() {
        runner.withPropertyValues(
                        "app.lgpd.consent-enforcement=off",
                        "app.lgpd.policy-version=",
                        "app.lgpd.terms-version=2026-06-30")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    @DisplayName("terms-version ausente derruba o contexto")
    void termsVersionAusenteFalha() {
        runner.withPropertyValues(
                        "app.lgpd.consent-enforcement=off",
                        "app.lgpd.policy-version=2026-06-30")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Configuration
    @EnableConfigurationProperties(LgpdProperties.class)
    static class TestConfig {
    }
}
