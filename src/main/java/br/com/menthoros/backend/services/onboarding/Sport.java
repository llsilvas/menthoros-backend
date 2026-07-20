package br.com.menthoros.backend.services.onboarding;

/**
 * Modalidade normalizada de uma atividade importada (design.md Decisao 1,
 * athlete-onboarding-baseline). Distinto de {@code TipoTreino} (estilo do
 * treino - intervalado, longo, etc.) — este enum classifica o esporte em si.
 */
public enum Sport {
    RUNNING,
    CYCLING,
    SWIMMING,
    OTHER
}
