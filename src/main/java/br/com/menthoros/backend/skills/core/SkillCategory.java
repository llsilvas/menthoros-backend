package br.com.menthoros.backend.skills.core;

/**
 * Categorias funcionais das skills de domínio.
 * Usadas para agrupamento, filtragem e priorização no SkillRegistry.
 */
public enum SkillCategory {

    /** Análise de recuperação e fadiga (TSB, ATL, CTL). */
    RECOVERY,

    /** Elegibilidade para treinos de alta intensidade (intervalado, etc.). */
    ELIGIBILITY,

    /** Guarda de segurança para prescrição (overtraining, carga excessiva). */
    PRESCRIPTION_GUARD,

    /** Análise de treinos realizados (qualidade, aderência ao plano). */
    WORKOUT_ANALYSIS,

    /** Análise de distribuição de carga e volume por zona. */
    DISTRIBUTION
}
