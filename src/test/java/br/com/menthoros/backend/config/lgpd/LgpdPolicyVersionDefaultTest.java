package br.com.menthoros.backend.config.lgpd;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guard-rail do bump (CA2 de lgpd-trajeto-cards-compartilhamento): o default de
 * {@code app.lgpd.policy-version} precisa ser a mesma data de {@code POLITICA_VERSAO} no frontend.
 * Divergência grava aceite de uma versão que o usuário não leu. A variável
 * {@code LGPD_POLICY_VERSION} do ambiente sobrescreve este default e é conferida no deploy.
 */
class LgpdPolicyVersionDefaultTest {

    @Test
    @DisplayName("default da versão vigente da Política é 2026-11-09")
    void defaultBateComOFrontend() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));

        assertThat(yaml.getObject().getProperty("app.lgpd.policy-version"))
                .isEqualTo("${LGPD_POLICY_VERSION:2026-11-09}");
    }
}
