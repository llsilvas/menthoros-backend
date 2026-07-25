package br.com.menthoros.backend.enums;

/**
 * Recomendação determinística de foco para a semana seguinte, congelada na
 * {@code RevisaoSemanal} no encerramento da semana (Fatia 1 — add-weekly-review-consolidation).
 *
 * <p>Derivada de {@code adherenceStatus} + {@code PlanoSemanal.tsbFim} (árvore em ADR-0006 / D5):
 * <ul>
 *   <li>{@code RECOVERY} — fadiga alta (TSB ≤ −25) ou baixa aderência com fadiga.</li>
 *   <li>{@code PROGRESS} — semana boa (aderência ALTA, TSB ≥ −10, dados suficientes, sem crítico faltando).</li>
 *   <li>{@code MAINTAIN} — default (inclui dados insuficientes e casos intermediários).</li>
 * </ul>
 */
public enum RecommendationType {
    RECOVERY,
    MAINTAIN,
    PROGRESS
}
