package br.com.menthoros.backend.domain.compliance;

import br.com.menthoros.backend.domain.planner.AthleteConstraints;
import br.com.menthoros.backend.domain.planner.ProvaSnapshot;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Contexto adicional que o {@code SkeletonComplianceChecker} precisa alem do
 * {@code WeekPlanSkeleton} e do plano gerado — a prova que definiu a fase (para o check de
 * sessao pesada perto da prova) e as constraints duras do atleta.
 */
public record ComplianceContext(
        Optional<ProvaSnapshot> provaDeterminante,
        AthleteConstraints constraints,
        LocalDate referenceDate
) {
}
