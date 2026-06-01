package br.com.menthoros.backend.skills.race.enums;

/** Tendência de CTL (Chronic Training Load) projetada até o dia da prova. */
public enum CtlTrend {
    /** CTL aumentando: (projectedCtl - currentCtl) / weeksToRace &gt; 0. */
    BUILDING,
    /** CTL estável: variação entre -1 e 0 por semana. */
    STABLE,
    /** CTL em queda: variação &lt; -1 por semana. */
    DECLINING
}
