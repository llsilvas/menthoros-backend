package br.com.menthoros.backend.domain.planner;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Valida constraints duras do {@link AthleteConstraints} (do {@link OnboardingContext} ou do
 * snapshot legado) contra as sessoes candidatas do {@link WeekPlanSkeleton}: dia disponivel,
 * numero maximo de sessoes, duracao maxima por sessao e equipamento indisponivel.
 */
@Component
public class ConstraintValidator {

    public ConstraintValidationResult validate(AthleteConstraints constraints, List<SessionSlot> sessoes) {
        List<ConstraintViolation> violations = new ArrayList<>();

        if (constraints.diasDisponiveis() != null) {
            for (SessionSlot sessao : sessoes) {
                if (!constraints.diasDisponiveis().contains(sessao.day())) {
                    violations.add(new ConstraintViolation(ConstraintViolationKey.DIA_INDISPONIVEL,
                            "Sessao em " + sessao.day() + " fora dos dias disponiveis do atleta"));
                }
            }
        }

        if (constraints.maxSessoesPorSemana() != null && sessoes.size() > constraints.maxSessoesPorSemana()) {
            violations.add(new ConstraintViolation(ConstraintViolationKey.MAX_SESSOES_EXCEDIDO,
                    sessoes.size() + " sessoes, maximo " + constraints.maxSessoesPorSemana()));
        }

        if (constraints.duracaoMaximaMinutos() != null) {
            for (SessionSlot sessao : sessoes) {
                if (sessao.durationMinutes() != null && sessao.durationMinutes() > constraints.duracaoMaximaMinutos()) {
                    violations.add(new ConstraintViolation(ConstraintViolationKey.DURACAO_MAXIMA_EXCEDIDA,
                            sessao.day() + " com " + sessao.durationMinutes() + "min"));
                }
            }
        }

        if (constraints.equipamentoIndisponivel() != null) {
            for (SessionSlot sessao : sessoes) {
                if (constraints.equipamentoIndisponivel().contains(sessao.sessionType())) {
                    violations.add(new ConstraintViolation(ConstraintViolationKey.EQUIPAMENTO_INDISPONIVEL,
                            sessao.sessionType() + " em " + sessao.day()));
                }
            }
        }

        return new ConstraintValidationResult(violations.isEmpty(), violations);
    }
}
