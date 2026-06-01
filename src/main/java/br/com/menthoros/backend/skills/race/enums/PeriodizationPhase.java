package br.com.menthoros.backend.skills.race.enums;

/**
 * Fase de periodização combinada com estado de TSB projetado para o dia da prova.
 * Determina o fator multiplicativo aplicado pelo PeriodizationAdjuster (Camada 3).
 * Não mapeia 1-para-1 com FasePeriodizacao — representa cenários de carga + forma.
 */
public enum PeriodizationPhase {
    /** Taper ótimo: TSB entre -5 e +10. Fator: 0.975 (ganho de 2.5%). */
    TAPER_OPTIMAL,
    /** Build no pico: TSB entre -15 e -5. Fator: 1.00 (neutro). */
    BUILD_PEAK,
    /** Específico com boa forma: TSB entre -5 e +5. Fator: 0.985. */
    ESPECIFICO_FRESH,
    /** Base / conservador: qualquer TSB em fase BASE. Fator: 1.05. */
    BASE_CONSERVATIVE,
    /**
     * Fadigado: TSB &lt; -15. Fator: 1.08 (penalidade de 8%).
     * Tem precedência sobre a fase de periodização.
     */
    FATIGUED,
    /** Super-tapering: TSB &gt; +15. Fator: 1.03 (perda de forma por destreino). */
    OVERTAPERED
}
