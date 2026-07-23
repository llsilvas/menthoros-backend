package br.com.menthoros.backend.services.onboarding;

/**
 * Origem de um valor calculado do baseline (design.md Decisao 1/3,
 * athlete-onboarding-baseline): {@code MEASURED} quando vem de dado real
 * suficiente (Cenario A), {@code ESTIMATED} quando parcial ou totalmente
 * extrapolado por heuristica (Cenarios B/C).
 */
public enum OrigemDado {
    MEASURED,
    ESTIMATED
}
