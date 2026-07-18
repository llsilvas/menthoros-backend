package br.com.menthoros.backend.domain.compliance;

/**
 * Versao do {@code PlannerEngine} persistida em {@code tb_plano_semanal.planner_version}
 * (design.md Decisao 9) — permite correlacionar auditoria com a logica que a gerou quando o
 * motor evoluir.
 */
public final class PlannerVersion {

    public static final String CURRENT = "planner-v1";

    private PlannerVersion() {
    }
}
