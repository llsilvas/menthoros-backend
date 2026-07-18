package br.com.menthoros.backend.domain.planner;

import java.time.LocalDate;
import java.util.List;

/**
 * Saida deterministica do {@link PlannerEngine#planWeek}. Em shadow mode, e usada apenas para
 * auditoria e compliance hipotetico — nao altera o prompt, o plano gerado nem a persistencia
 * do treino (CA12).
 */
public record WeekPlanSkeleton(
        TrainingPhase phase,
        WeeklyLoadTarget loadTarget,
        List<SessionSlot> sessions,
        InjuryRiskAssessment injuryRisk,
        ConstraintValidationResult constraints,
        boolean requiresCoachReview,
        LocalDate referenceDate
) {
}
