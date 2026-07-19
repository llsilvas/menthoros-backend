package br.com.menthoros.backend.domain.planner;

import java.util.ArrayList;
import java.util.List;

/**
 * Valida constraints duras do {@link AthleteConstraints} (do {@link OnboardingContext} ou do
 * snapshot legado) contra as sessoes candidatas do {@link WeekPlanSkeleton}: dia disponivel,
 * numero maximo de sessoes, duracao maxima por sessao e equipamento indisponivel.
 */
public class ConstraintValidator {

    public ConstraintValidationResult validate(AthleteConstraints constraints, List<SessionSlot> sessoes) {
        List<String> violations = new ArrayList<>();

        if (constraints.diasDisponiveis() != null) {
            for (SessionSlot sessao : sessoes) {
                if (!constraints.diasDisponiveis().contains(sessao.day())) {
                    violations.add("DIA_INDISPONIVEL: " + sessao.day());
                }
            }
        }

        if (constraints.maxSessoesPorSemana() != null && sessoes.size() > constraints.maxSessoesPorSemana()) {
            violations.add("MAX_SESSOES_EXCEDIDO: " + sessoes.size() + " sessoes, maximo " + constraints.maxSessoesPorSemana());
        }

        if (constraints.duracaoMaximaMinutos() != null) {
            for (SessionSlot sessao : sessoes) {
                if (sessao.durationMinutes() != null && sessao.durationMinutes() > constraints.duracaoMaximaMinutos()) {
                    violations.add("DURACAO_MAXIMA_EXCEDIDA: " + sessao.day() + " com " + sessao.durationMinutes() + "min");
                }
            }
        }

        if (constraints.equipamentoIndisponivel() != null) {
            for (SessionSlot sessao : sessoes) {
                if (constraints.equipamentoIndisponivel().contains(sessao.sessionType())) {
                    violations.add("EQUIPAMENTO_INDISPONIVEL: " + sessao.sessionType() + " em " + sessao.day());
                }
            }
        }

        return new ConstraintValidationResult(violations.isEmpty(), violations);
    }
}
