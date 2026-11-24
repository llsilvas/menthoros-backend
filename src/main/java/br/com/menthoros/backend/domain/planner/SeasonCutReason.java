package br.com.menthoros.backend.domain.planner;

/**
 * Proteção que tirou o alvo de carga abaixo do {@code targetTss} da temporada
 * (add-season-plan-load-enforcement). Nula no {@link WeeklyLoadTarget} quando o alvo é o da temporada.
 */
public enum SeasonCutReason {
    /** Lesão ({@code RECOVERY}) ou pós-prova: metade da base da temporada. */
    REDUCAO_FASE,
    /** Progressão em {@code REDUZIR}: CTL atual com o ajuste negativo. */
    PROGRESSAO_REDUZIR,
    /** Teto de rampa de CTL ({@code 7 × (ctl + 8)}). */
    TETO_RAMPA,
    /** Regime cold-start da calibração (ADR-0012). */
    COLD_START,
    /** Taper ou semana de prova: não passa da carga atual ({@code ctl × 7}), como no legado. */
    CONTENCAO_PROVA
}
