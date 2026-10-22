package br.com.menthoros.backend.enums;

/**
 * Marca do relogio/dispositivo do atleta (retrofit 10.6, athlete-onboarding-baseline,
 * sessao de grilling 2026-07-21) — obrigatoria no onboarding. Alimenta o
 * {@code ConfidenceScorer} como prior antes de qualquer atividade real existir, na mesma
 * escala de {@code FontePriority} (menor numero = maior prioridade). A capacidade por
 * dispositivo (nem todo modelo suporta potencia de corrida/running dynamics) e tratada pela
 * change add-watch-brand-connection-guide e por C3/C4 do levantamento.
 *
 * <p>Novos valores entram sempre ao FINAL (a coluna guarda o nome, mas a ordem e contrato visual).
 */
public enum DispositivoMarca {
    GARMIN(1),
    COROS(2),
    POLAR(2),
    SUUNTO(2),
    APPLE(3),
    OUTRO(4),
    AMAZFIT(3),
    SAMSUNG(3);

    private final int prior;

    DispositivoMarca(int prior) {
        this.prior = prior;
    }

    /** Menor numero = maior prioridade; so prior &lt;= 2 pontua "Fonte confiavel" sem historico. */
    public int prior() {
        return prior;
    }
}
