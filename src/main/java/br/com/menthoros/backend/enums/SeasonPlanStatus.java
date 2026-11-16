package br.com.menthoros.backend.enums;

/**
 * Ciclo de vida do plano de temporada ({@code PlanoTreino.seasonStatus}).
 * <ul>
 *   <li>{@code PROPOSED} — gerado, aguardando aprovação do treinador.</li>
 *   <li>{@code ACTIVE} — aprovado e guiando a geração semanal; no máximo um por atleta (índice parcial).</li>
 *   <li>{@code ARCHIVED} — substituído por outra versão ou com a prova-alvo cancelada.</li>
 *   <li>{@code COMPLETED} — a prova-alvo aconteceu.</li>
 * </ul>
 * Nulo em {@code PlanoTreino} legado, anterior ao plano de temporada.
 */
public enum SeasonPlanStatus {
    PROPOSED,
    ACTIVE,
    ARCHIVED,
    COMPLETED
}
