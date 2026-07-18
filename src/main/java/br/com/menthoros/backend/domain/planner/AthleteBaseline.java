package br.com.menthoros.backend.domain.planner;

import java.time.LocalDate;

/**
 * Contrato minimo reservado para {@code athlete-onboarding-baseline} (design.md Decisao 2,
 * {@code athlete-onboarding-baseline/proposal.md:3,52,55}). Esta change so le a forma; quem
 * calcula o baseline (Confidence Scorer, Calibration Phase) e o change-irmao.
 */
public record AthleteBaseline(
        Double ctlEstimado,
        LocalDate dataEstimativa
) {
}
