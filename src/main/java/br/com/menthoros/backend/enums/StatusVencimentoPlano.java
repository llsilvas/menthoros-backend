package br.com.menthoros.backend.enums;

import java.time.LocalDate;

/** Status de vencimento do plano do atleta — derivado em tempo de leitura, nunca persistido. */
public enum StatusVencimentoPlano {

    EM_DIA,
    PROXIMO_VENCIMENTO,
    VENCIDO;

    private static final int DIAS_ALERTA_VENCIMENTO = 7;

    public static StatusVencimentoPlano resolver(LocalDate dataVencimento, LocalDate hoje) {
        if (dataVencimento == null) {
            return null;
        }
        if (dataVencimento.isBefore(hoje)) {
            return VENCIDO;
        }
        if (!dataVencimento.isAfter(hoje.plusDays(DIAS_ALERTA_VENCIMENTO))) {
            return PROXIMO_VENCIMENTO;
        }
        return EM_DIA;
    }
}
