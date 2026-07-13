package br.com.menthoros.backend.enums;

/**
 * Motivo pelo qual uma métrica de decoupling não foi calculada — exposto na API para que a UI
 * explique um valor ausente em vez de parecer inconsistência (design D4 de fit-lap-derived-metrics).
 */
public enum MotivoNullDecoupling {
    SEM_ETAPAS,
    TIPO_NAO_CONTINUO,
    SEGMENTOS_INSUFICIENTES,
    DURACAO_INSUFICIENTE,
    VARIABILIDADE_ALTA,
    COBERTURA_POTENCIA_INSUFICIENTE,
    DADOS_INVALIDOS
}
