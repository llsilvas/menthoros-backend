package br.com.menthoros.backend.enums;

/** Por que um pull não foi {@link ResultadoPull#COMPLETO} ({@code tb_sync_pull_log.erro_categoria}). */
public enum ErroCategoriaPull {
    RATE_LIMIT,
    CREDENCIAL,
    TRANSITORIO,
    DADOS_INVALIDOS,
    INESPERADO
}
