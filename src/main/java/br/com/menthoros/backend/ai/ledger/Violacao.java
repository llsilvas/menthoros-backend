package br.com.menthoros.backend.ai.ledger;

/**
 * Violação gravada em {@code tb_llm_call.violations} — mesmo formato de {@code PlannerViolation}
 * e {@code ViolacaoQualidade} (key + mensagem), serializado como lista JSON.
 */
public record Violacao(String key, String mensagem) {

    public Violacao {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key da violação é obrigatória");
        }
    }
}
