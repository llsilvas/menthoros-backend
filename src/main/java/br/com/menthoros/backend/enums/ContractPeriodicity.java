package br.com.menthoros.backend.enums;

/**
 * Periodicidade do contrato do atleta com a assessoria — distinto de {@link PlanoAssessoria},
 * que é o plano SaaS da assessoria com a Menthoros. Substitui {@code TipoPlanoAtleta}.
 */
public enum ContractPeriodicity {

    MONTHLY(1),
    QUARTERLY(3),
    SEMIANNUAL(6),
    ANNUAL(12);

    private final int months;

    ContractPeriodicity(int months) {
        this.months = months;
    }

    public int months() {
        return months;
    }
}
