package br.com.menthoros.backend.domain.planner;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Saida deterministica do {@link PlannerEngine#planWeek}. Em shadow mode, e usada apenas para
 * auditoria e compliance hipotetico — nao altera o prompt, o plano gerado nem a persistencia
 * do treino (CA12).
 *
 * <p>{@code provaDeterminante} e exposta para o caller (o shadow, secao 7) montar o
 * {@code ComplianceContext} do {@code SkeletonComplianceChecker} sem recalcular a selecao de
 * prova — e a mesma prova que o {@code PeriodizationPlanner} usou para resolver {@code phase}.
 *
 * <p>{@code coachReviewReason} unifica o motivo de {@code requiresCoachReview} — que pode vir
 * do {@code InjuryPolicyResolver} (lesao ativa/recente/nao-estruturada) ou do
 * {@code InjuryRiskEvaluator} (TSB baixo) — num unico campo rastreavel pelo caller (metricas de
 * calibracao). {@code null} quando {@code requiresCoachReview} e {@code false}.
 */
public record WeekPlanSkeleton(
        TrainingPhase phase,
        WeeklyLoadTarget loadTarget,
        List<SessionSlot> sessions,
        InjuryRiskAssessment injuryRisk,
        ConstraintValidationResult constraints,
        boolean requiresCoachReview,
        String coachReviewReason,
        LocalDate referenceDate,
        String plannerScope,
        Optional<ProvaSnapshot> provaDeterminante
) {
}
