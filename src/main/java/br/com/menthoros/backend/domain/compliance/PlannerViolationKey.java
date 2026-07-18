package br.com.menthoros.backend.domain.compliance;

/**
 * Identifica o tipo de violacao detectada pelo {@code SkeletonComplianceChecker}. Tipo proprio
 * — nao estende {@code ConstraintKey} (design.md Decisao 4), que e um switch fechado de checks
 * do {@code PlanQualityChecker} legado, sem chaves para fase/TSS/longao/prova/taper.
 */
public enum PlannerViolationKey {
    FASE_DIVERGENTE,
    SESSION_COUNT_DIVERGENTE,
    TSS_FORA_DA_FAIXA,
    LONGO_ACIMA_TETO,
    EXCESSO_INTENSIDADE,
    SESSAO_PESADA_PROXIMA_PROVA,
    CONSTRAINT_DURA_VIOLADA,
    DIA_INDISPONIVEL,
    TAPER_VIOLADO
}
