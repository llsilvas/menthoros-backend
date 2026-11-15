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

    /**
     * Todas as rotas configuradas — fonte única para validações que precisam
     * varrer a topologia (ex.: {@code LlmPricingRegistry}); adicionar uma rota
     * nova aqui a inclui automaticamente nessas validações.
     */
    public java.util.List<RotaLlm> todasAsRotas() {
        return java.util.List.of(simple, standard, complex, expert, plano);
    }

    private static final java.util.Set<String> ESFORCOS =
            java.util.Set.of("none", "low", "medium", "high", "xhigh", "max");

    /**
     * Modelos sem reasoning: rejeitam {@code reasoning_effort} e {@code verbosity}. Prefixo cobre os
     * snapshots datados que a OpenAI devolve.
     */
    private static final java.util.List<String> PREFIXOS_LEGADOS = java.util.List.of("gpt-4o", "gpt-4.1");

    /**
     * Falha no boot, pelo esforço efetivo e não pela presença do campo: {@code none} + temperature é
     * válido (candidato mais próximo do comportamento atual); qualquer outro esforço proíbe
     * {@code temperature} e {@code top-p}, que a OpenAI rejeita com 400.
     */
    @jakarta.annotation.PostConstruct
    void validarPerfis() {
        validar("simple", simple);
        validar("standard", standard);
        validar("complex", complex);
        validar("expert", expert);
        validar("plano", plano);
    }

    private static void validar(String nomeRota, RotaLlm rota) {
        if (rota == null) {
            return;
        }
        String esforco = rota.getReasoningEffort();
        if (esforco != null && !ESFORCOS.contains(esforco)) {
            throw new IllegalStateException("app.llm.routing." + nomeRota + ".reasoning-effort='" + esforco
                    + "' inválido; aceitos: " + ESFORCOS);
        }
        boolean legado = rota.getModel() != null
                && PREFIXOS_LEGADOS.stream().anyMatch(rota.getModel()::startsWith);
        if (legado && esforco != null) {
            throw new IllegalStateException("app.llm.routing." + nomeRota + ".reasoning-effort não é aceito"
                    + " pelo modelo legado " + rota.getModel());
        }
        if (legado && rota.getVerbosity() != null) {
            throw new IllegalStateException("app.llm.routing." + nomeRota + ".verbosity não é aceito"
                    + " pelo modelo legado " + rota.getModel());
        }
        boolean comReasoning = esforco != null && !"none".equals(esforco);
        if (comReasoning && rota.getTemperature() != null) {
            throw new IllegalStateException("app.llm.routing." + nomeRota + ".temperature não é aceito"
                    + " com reasoning-effort=" + esforco);
        }
        if (comReasoning && rota.getTopP() != null) {
            throw new IllegalStateException("app.llm.routing." + nomeRota + ".top-p não é aceito"
                    + " com reasoning-effort=" + esforco);
        }
    }

    @Getter
    @Setter
    public static class RotaLlm {

        @NotBlank
        private String model;

        /**
         * Opcional: modelos com reasoning ativo rejeitam {@code temperature} (HTTP 400).
         */
        private Double temperature;

        @NotNull
        private Integer maxTokens;

        /** Só OpenAI sem reasoning ativo; proibido com esforço diferente de {@code none}. */
        private Double topP;

        /** {@code none|low|medium|high|xhigh|max}; ausente = padrão do modelo. */
        private String reasoningEffort;

        /** Só modelos com reasoning; proibido em modelo legado. */
        private String verbosity;

        /**
         * Teto de duração da chamada HTTP ao modelo desta rota (ADR-0008).
         *
         * Mora aqui, e não no provider, porque {@code SIMPLE} (1k tokens) e
         * {@code PLANO} (12k) são servidos pelo mesmo provider — um teto único
         * acoplaria justamente o par com a maior diferença de latência.
         *
         * Valores atuais são derivados de {@code maxTokens}, não medidos: só o
         * {@code PLANO} tem número (~80s, comentário em {@code PlanoResilienceService}).
         * Recalibrar com o {@code Timer} por rota do {@code CostTrackingAdvisor}.
         */
        @NotNull
        private java.time.Duration timeout;
    }
}
