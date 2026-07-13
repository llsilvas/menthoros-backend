package br.com.menthoros.backend.enums;

/**
 * Motivo pelo qual uma volta ficou fora da série de eficiência — exposto para a UI reconciliar
 * o gráfico com buracos com o decoupling escalar (design D1 de fit-lap-derived-metrics).
 */
public enum MotivoOmissaoVolta {
    SEM_FC,
    SEM_VELOCIDADE,
    DURACAO_INVALIDA
}
