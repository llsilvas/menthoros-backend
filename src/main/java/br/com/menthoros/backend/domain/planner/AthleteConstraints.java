package br.com.menthoros.backend.domain.planner;

import java.time.DayOfWeek;
import java.util.List;

/**
 * Constraints duras do atleta consumidas pelo {@code ConstraintValidator}. Forma reservada
 * para {@code athlete-onboarding-baseline} popular (design.md Decisao 2); nomes de campo
 * alinhados aos campos de onboarding (diasDisponiveis, duracaoDisponivel).
 */
public record AthleteConstraints(
        List<DayOfWeek> diasDisponiveis,
        Integer maxSessoesPorSemana,
        Integer duracaoMaximaMinutos,
        List<String> equipamentoIndisponivel
) {
}
