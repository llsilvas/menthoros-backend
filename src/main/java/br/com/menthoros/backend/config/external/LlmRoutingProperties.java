package br.com.menthoros.backend.config.external;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * Parâmetros de roteamento multi-modelo (RISK-01 / ADR-FIN-02): model ID,
 * temperature e maxTokens de cada rota vêm do {@code application.yml}
 * (prefixo {@code app.llm.routing}), permitindo troca de modelo por
 * ambiente sem recompilação.
 *
 * As 5 rotas são obrigatórias — contexto falha rápido se alguma estiver
 * ausente ou incompleta.
 */
@Getter
@Setter
@Validated
@Configuration
@ConfigurationProperties(prefix = "app.llm.routing")
public class LlmRoutingProperties {

    @NotNull @Valid
    private RotaLlm simple;

    @NotNull @Valid
    private RotaLlm standard;

    @NotNull @Valid
    private RotaLlm complex;

    @NotNull @Valid
    private RotaLlm expert;

    @NotNull @Valid
    private RotaLlm plano;

    @Getter
    @Setter
    public static class RotaLlm {

        @NotBlank
        private String model;

        @NotNull
        private Double temperature;

        @NotNull
        private Integer maxTokens;
    }
}
