package br.com.menthoros.backend.enums;

/**
 * Nível de aderência da semana, por contagem de treinos realizados/planejados na janela
 * exata do {@code PlanoSemanal} (Fatia 1 — add-weekly-review-consolidation).
 *
 * <p>{@code ALTA} ≥ 90% · {@code MEDIA} 60–89% · {@code BAIXA} &lt; 60% ou ≥1 treino de alta
 * criticidade ({@code TipoTreino.getFatorImpacto() ≥ 1.15}) não realizado. Polaridade
 * <b>boa = ALTA</b> — distinto de {@link Severidade} (onde ALTA = problema grave).
 */
public enum NivelAderencia {
    ALTA,
    MEDIA,
    BAIXA
}
