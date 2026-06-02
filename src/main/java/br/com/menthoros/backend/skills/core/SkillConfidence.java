package br.com.menthoros.backend.skills.core;

/**
 * Nível de confiança no resultado de uma skill de domínio.
 * Depende da qualidade e quantidade dos dados de entrada disponíveis.
 */
public enum SkillConfidence {

    /** Dados insuficientes ou de baixa qualidade — resultado deve ser tratado com cautela. */
    LOW,

    /** Dados suficientes para uma análise razoável. */
    MEDIUM,

    /** Dados completos e de alta qualidade — resultado confiável. */
    HIGH
}
