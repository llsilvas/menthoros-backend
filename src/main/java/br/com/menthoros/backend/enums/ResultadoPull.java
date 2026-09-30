package br.com.menthoros.backend.enums;

/**
 * Desfecho de um ciclo de pull de atividades de um atleta ({@code tb_sync_pull_log.resultado}).
 * {@code PARCIAL} = houve progresso confirmado, mas algo ficou para o próximo ciclo.
 */
public enum ResultadoPull {
    COMPLETO,
    PARCIAL,
    FALHA
}
