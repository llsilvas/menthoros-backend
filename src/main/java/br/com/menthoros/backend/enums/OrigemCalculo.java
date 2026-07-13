package br.com.menthoros.backend.enums;

/**
 * Granularidade de origem de uma métrica derivada — permite à UI sinalizar quando a precisão
 * mudou (ex.: supersessão lap-based → sample-based pelo workout-metrics-analyzer) em vez de
 * trocar números confiáveis silenciosamente.
 */
public enum OrigemCalculo {
    POR_VOLTA,
    POR_AMOSTRA
}
