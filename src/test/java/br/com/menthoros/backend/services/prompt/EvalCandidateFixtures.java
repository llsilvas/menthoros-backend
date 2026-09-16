package br.com.menthoros.backend.services.prompt;

import br.com.menthoros.backend.domain.planner.AthleteSnapshot;
import br.com.menthoros.backend.domain.planner.ConstraintValidator;
import br.com.menthoros.backend.domain.planner.InjuryPolicyResolver;
import br.com.menthoros.backend.domain.planner.InjuryRiskEvaluator;
import br.com.menthoros.backend.domain.planner.LoadTargetResolver;
import br.com.menthoros.backend.domain.planner.PeriodizationPlanner;
import br.com.menthoros.backend.domain.planner.PlannerEngine;
import br.com.menthoros.backend.domain.planner.PlannerInputSnapshot;
import br.com.menthoros.backend.domain.planner.ProvaSnapshot;
import br.com.menthoros.backend.domain.planner.TaperStrategy;
import br.com.menthoros.backend.domain.planner.WeekPlanSkeleton;
import br.com.menthoros.backend.dto.DecisaoProgressao;
import br.com.menthoros.backend.dto.ProgressaoHistoricoResumo;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.enums.EstadoProgressao;
import br.com.menthoros.backend.services.prompt.PlanoPromptArquetipos.Arquetipo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Fixtures de candidato (plan-generation-eval-set, fatia 1, task 1.4) — estendem os 5 arquétipos
 * de {@link PlanoPromptArquetipos} com um {@link WeekPlanSkeleton} construído fresco via
 * {@link PlannerEngine} (mesma construção de {@code PlannerEngineGoldenSetTest}), para exercitar
 * o grader determinístico completo (inclusive {@code SkeletonComplianceChecker}) em modo
 * candidato. Nunca reconstrói um skeleton "histórico" — é sempre novo, coerente com o
 * {@link Arquetipo} sintético (ver design.md "Correção de escopo", rodada 2 de DoR).
 *
 * <p>{@code decisaoProgressao}/{@code revisaoConsumida} permanecem {@code null} — mesma
 * simplificação já aceita em {@code PlanoTreinoPromptBuilderGoldenTest}, que também não os
 * exercita.
 */
final class EvalCandidateFixtures {

    private EvalCandidateFixtures() {
    }

    /** Um arquétipo + o skeleton fresco correspondente (pode ser {@code null} — nunca é hoje, mas
     * o contrato permanece opcional para consistência com produção: skeleton ausente = sem
     * compliance a checar). */
    record Candidato(Arquetipo arquetipo, WeekPlanSkeleton skeleton) {
    }

    static List<Candidato> todos() {
        PlannerEngine engine = new PlannerEngine(
                new PeriodizationPlanner(), new LoadTargetResolver(), new TaperStrategy(),
                new InjuryRiskEvaluator(), new InjuryPolicyResolver(), new ConstraintValidator());
        return PlanoPromptArquetipos.todos().stream()
                .map(arq -> new Candidato(arq, engine.planWeek(paraSnapshot(arq))))
                .toList();
    }

    private static PlannerInputSnapshot paraSnapshot(Arquetipo arq) {
        Atleta atleta = arq.atleta();
        PlanoMetaDados meta = arq.meta();
        AthleteSnapshot athleteSnapshot = new AthleteSnapshot(
                UUID.randomUUID(), atleta.getNivelExperiencia(),
                Boolean.TRUE.equals(atleta.getTemLesao()), atleta.getDescricaoLesao(),
                atleta.getDataUltimaLesao(), atleta.getDiasDisponiveis(), null);
        DecisaoProgressao decisao = new DecisaoProgressao(EstadoProgressao.MANTER, 0.0, 0, true,
                "eval candidato");
        ProgressaoHistoricoResumo historico = new ProgressaoHistoricoResumo(
                0, 0, 0.0, 0.0, 0.0, 0, 0, null, meta.getTsbAtual(), meta.getCtlAtual(), 0.0,
                meta.getSemanasProgressaoContinua() != null ? meta.getSemanasProgressaoContinua() : 0);
        List<ProvaSnapshot> provas = provaOuVazio(arq.prova());

        return new PlannerInputSnapshot(athleteSnapshot, decisao, historico, provas, List.of(),
                Optional.empty(), arq.inicioSemana(), 30);
    }

    private static List<ProvaSnapshot> provaOuVazio(Prova prova) {
        if (prova == null) {
            return List.of();
        }
        return List.of(new ProvaSnapshot(prova.getDataProva(),
                prova.getDistanciaKm() != null ? prova.getDistanciaKm().doubleValue() : null,
                prova.isProvaAlvo(), false));
    }
}
