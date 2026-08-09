package br.com.menthoros.backend.enums;

/**
 * Estados de uma tentativa de auto-cadastro.
 *
 * <p>A ordem das constantes é a ordem real do provisionamento — e é ela que a compensação percorre
 * ao contrário. Os valores espelham o CHECK da V75: adicionar um estado aqui sem adicioná-lo lá faz
 * o INSERT falhar.</p>
 */
public enum SignupProvisioningStatus {

    STARTED,
    ASSESSORIA_CREATED,
    ORGANIZATION_CREATED,
    KEYCLOAK_USER_CREATED,
    LOCAL_USER_CREATED,
    VERIFICATION_EMAIL_SENT,
    ACTIVE,

    /** Falhou e a compensação limpou tudo — nada ficou para trás. */
    FAILED,

    /**
     * Falhou e a compensação <strong>também</strong> falhou: há recurso órfão no Keycloak.
     * Exige intervenção humana; é o que a varredura operacional procura.
     */
    RECONCILIATION_REQUIRED
}
