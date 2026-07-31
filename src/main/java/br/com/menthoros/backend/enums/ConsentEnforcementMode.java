package br.com.menthoros.backend.enums;

/**
 * Estágio de rollout do enforcement de consentimento LGPD do coach.
 *
 * <p>Existe porque ligar o bloqueio junto com o deploy travaria a escrita de <b>todos</b> os
 * coaches de uma vez — nenhum tem consentimento registrado no momento em que a V73 sobe. Também é
 * o botão de pânico: reverter é mudar configuração, sem redeploy.
 */
public enum ConsentEnforcementMode {

    /** Não bloqueia nem registra. O modal já aparece e o aceite já é gravado. */
    OFF,

    /**
     * Não bloqueia, mas loga cada request que <b>seria</b> bloqueada. É o insumo para saber quando
     * é seguro virar {@link #ON}.
     */
    REPORT_ONLY,

    /** Bloqueia escrita de coach sem consentimento com {@code 403 LGPD_CONSENT_REQUIRED}. */
    ON
}
