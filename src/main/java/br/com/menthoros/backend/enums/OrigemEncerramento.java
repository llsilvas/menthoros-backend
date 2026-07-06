package br.com.menthoros.backend.enums;

/**
 * Origem do encerramento de uma semana de treino.
 * <ul>
 *   <li>{@code ON_DEMAND} — ação explícita do treinador (individual ou lote).</li>
 *   <li>{@code AUTOMATICO} — fechamento pelo scheduler (fallback com carência).</li>
 * </ul>
 */
public enum OrigemEncerramento {
    ON_DEMAND,
    AUTOMATICO
}
