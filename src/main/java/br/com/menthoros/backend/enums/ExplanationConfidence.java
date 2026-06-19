package br.com.menthoros.backend.enums;

/**
 * Grau de confiança em uma explicação de recomendação.
 *
 * <ul>
 *   <li>HIGH — derivado de regras determinísticas (thresholds fixos, flags de plano, contagens)</li>
 *   <li>MEDIUM — derivado de heurísticas ou dados parciais</li>
 *   <li>LOW — derivado de modelo LLM ou dados muito escassos</li>
 * </ul>
 *
 * Na v1 (fila de atenção determinística), todos os sinais produzem {@code HIGH}.
 */
public enum ExplanationConfidence {
    HIGH,
    MEDIUM,
    LOW
}
