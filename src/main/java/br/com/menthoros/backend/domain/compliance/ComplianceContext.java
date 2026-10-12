package br.com.menthoros.backend.domain.compliance;

import br.com.menthoros.backend.domain.planner.AthleteConstraints;
import br.com.menthoros.backend.domain.planner.ProvaSnapshot;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Contexto adicional que o {@code SkeletonComplianceChecker} precisa alem do
 * {@code WeekPlanSkeleton} e do plano gerado — a prova que definiu a fase (para o check de
 * sessao pesada perto da prova), as constraints duras do atleta e as provas cuja data real cai na
 * semana (para isentar o dia da prova de "dia indisponivel" — fix-planner-ignora-prova-na-semana).
 */
public record ComplianceContext(
        Optional<ProvaSnapshot> provaDeterminante,
        AthleteConstraints constraints,
        LocalDate referenceDate,
        List<ProvaSnapshot> provasDaSemana
) {
    public ComplianceContext {
        provasDaSemana = provasDaSemana == null ? List.of() : List.copyOf(provasDaSemana);
    }

    /** Semana sem prova. */
    public ComplianceContext(Optional<ProvaSnapshot> provaDeterminante,
                             AthleteConstraints constraints,
                             LocalDate referenceDate) {
        this(provaDeterminante, constraints, referenceDate, List.of());
    }

    /** Datas reais das provas da semana — a isencao e por data, nao por tipo de sessao. */
    public Set<LocalDate> datasDeProva() {
        return provasDaSemana.stream().map(ProvaSnapshot::dataProva).collect(Collectors.toSet());
    }
}
