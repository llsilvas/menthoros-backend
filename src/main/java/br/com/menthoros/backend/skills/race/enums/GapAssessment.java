package br.com.menthoros.backend.skills.race.enums;

/**
 * Avaliação do gap entre a meta do coach e a projeção atual (D8).
 * Exposto somente no painel do coach — nunca para o atleta.
 */
public enum GapAssessment {
    /** Gap ≤ 2%: dentro da margem de erro da projeção. */
    ON_TRACK,
    /** Gap 2–5%: possível com boa preparação. */
    REACHABLE,
    /** Gap 5–10%: exige tudo correr bem. */
    STRETCH,
    /** Gap &gt; 10%: distância significativa em relação à forma atual. */
    UNLIKELY
}
