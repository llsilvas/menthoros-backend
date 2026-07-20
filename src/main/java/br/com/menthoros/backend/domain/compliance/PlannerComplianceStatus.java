package br.com.menthoros.backend.domain.compliance;

/**
 * Status resumido de compliance do {@code WeekPlanSkeleton}, persistido em
 * {@code tb_plano_semanal.planner_compliance_status} (design.md Decisao 9).
 */
public enum PlannerComplianceStatus {
    NOT_EVALUATED,
    COMPLIANT,
    VIOLATIONS_DETECTED
}
