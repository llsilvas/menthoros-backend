package br.com.menthoros.backend.domain.planner;

import br.com.menthoros.backend.dto.DecisaoProgressao;
import br.com.menthoros.backend.dto.ProgressaoHistoricoResumo;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Anti-corruption layer entre a camada de service (entidades JPA) e o {@link PlannerEngine}
 * (design.md Decisao 17). O mapper entity-&gt;record fica na camada de service — o dominio
 * nunca ve {@code DadosPlanoDto} nem entidades.
 *
 * <p>{@code referenceDate} e sempre explicito: o {@link PlannerEngine} nunca chama
 * {@code LocalDate.now()} internamente, garantindo determinismo do golden set.
 *
 * <p>{@code progressaoHistorico} e reuso direto de {@code ProgressaoHistoricoResumo} (ja um
 * record puro, sem JPA) — traz {@code ctlAtual}/{@code tsbAtual} (design.md Decisao 15,
 * {@code InjuryRiskEvaluator}) e {@code semanasProgressaoContinua} (CA2, step-back). O
 * {@code historico} bruto por dia fica em {@code TreinoRealizadoSnapshot} para a janela de
 * monotonia de 7 dias.
 */
public record PlannerInputSnapshot(
        AthleteSnapshot athlete,
        DecisaoProgressao decisaoProgressao,
        ProgressaoHistoricoResumo progressaoHistorico,
        List<ProvaSnapshot> provas,
        List<TreinoRealizadoSnapshot> historico,
        Optional<OnboardingContext> onboardingContext,
        LocalDate referenceDate,
        int injuryRecentWindowDays
) {
}
