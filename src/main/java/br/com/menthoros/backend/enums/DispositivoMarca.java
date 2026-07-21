package br.com.menthoros.backend.enums;

/**
 * Marca do relogio/dispositivo do atleta (retrofit 10.6, athlete-onboarding-baseline,
 * sessao de grilling 2026-07-21) — obrigatoria no onboarding. Alimenta o
 * {@code ConfidenceScorer} como prior via {@code FontePriority} antes de qualquer
 * atividade real existir; tambem base para uma feature futura de capacidade por
 * dispositivo (nem todo modelo suporta potencia de corrida/running dynamics).
 */
public enum DispositivoMarca {
    GARMIN,
    COROS,
    POLAR,
    SUUNTO,
    APPLE,
    OUTRO
}
