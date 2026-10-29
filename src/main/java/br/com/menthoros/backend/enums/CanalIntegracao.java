package br.com.menthoros.backend.enums;

/**
 * Canal de integracao de treinos do atleta (retrofit 10.6, athlete-onboarding-baseline,
 * ADR-0003) — obrigatorio no onboarding. {@code STRAVA} nao e oferecido para atletas
 * novos (descontinuacao anunciada); atletas ja conectados via Strava continuam
 * funcionando pelo pipeline existente, so nao aparece como opcao no formulario.
 * {@code GARMIN} (garmin-connection, ADR-0013) e o canal direto dos atletas com relogio Garmin.
 */
public enum CanalIntegracao {
    INTERVALS_ICU,
    MANUAL,
    GARMIN
}
