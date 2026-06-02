package br.com.menthoros.backend.skills.core;

/**
 * Severidade do resultado de uma skill de domínio.
 * Usada pelo SkillOrchestratorService e pelo PlanoTreinoPromptBuilder para priorizar alertas.
 *
 * <p>Ordenação crescente de impacto: INFO < WARNING < CRITICAL < BLOCKER.</p>
 */
public enum SkillSeverity {

    /** Informação sem impacto na prescrição. */
    INFO,

    /** Alerta que deve ser considerado na prescrição, mas não a bloqueia. */
    WARNING,

    /** Problema crítico que deve ser endereçado na prescrição. */
    CRITICAL,

    /** Bloqueador que impede a prescrição de treinos intensos. */
    BLOCKER
}
