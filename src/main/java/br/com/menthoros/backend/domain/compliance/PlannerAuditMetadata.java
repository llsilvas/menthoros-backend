package br.com.menthoros.backend.domain.compliance;

import br.com.menthoros.backend.domain.planner.TrainingPhase;

/**
 * Resumo compacto do {@code WeekPlanSkeleton} persistido em
 * {@code tb_plano_semanal.planner_metadata_json} — sem prompt nem dado sensivel
 * (design.md Decisao 9).
 */
public record PlannerAuditMetadata(
        TrainingPhase phase,
        boolean requiresCoachReview,
        PlannerComplianceStatus complianceStatus,
        int violationCount,
        String plannerVersion
) {
}
