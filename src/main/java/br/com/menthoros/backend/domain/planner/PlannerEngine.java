package br.com.menthoros.backend.domain.planner;

import br.com.menthoros.backend.enums.DiaSemana;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Servico de dominio puro (design.md Decisao 1): sem IO, sem JPA, sem LLM, nunca chama
 * {@code LocalDate.now()}. Orquestra periodizacao, alvo de carga, taper, risco de lesao,
 * politica de lesao e validacao de constraints num unico {@link WeekPlanSkeleton}.
 *
 * <p>Hierarquia P0 (Decisao 6): (1) constraints duras e lesao ativa, (2) risco fisiologico,
 * (3) prova na semana / taper / pos-prova, (4) {@code DecisaoProgressao}, (5) preferencias do
 * atleta/coach. O override de fase por lesao (passo 1) tem precedencia sobre a fase resolvida
 * pela periodizacao (passo 3).
 *
 * <p>Registrado no Spring ({@code @Service}) mas puro — sem {@code @Transactional}, sem
 * dependencia de infraestrutura; o registro existe apenas para o
 * {@code PlannerShadowService} injetar via construtor.
 */
@Service
public class PlannerEngine {

    private final PeriodizationPlanner periodizationPlanner;
    private final LoadTargetResolver loadTargetResolver;
    private final TaperStrategy taperStrategy;
    private final InjuryRiskEvaluator injuryRiskEvaluator;
    private final InjuryPolicyResolver injuryPolicyResolver;
    private final ConstraintValidator constraintValidator;

    public PlannerEngine(PeriodizationPlanner periodizationPlanner,
                          LoadTargetResolver loadTargetResolver,
                          TaperStrategy taperStrategy,
                          InjuryRiskEvaluator injuryRiskEvaluator,
                          InjuryPolicyResolver injuryPolicyResolver,
                          ConstraintValidator constraintValidator) {
        this.periodizationPlanner = periodizationPlanner;
        this.loadTargetResolver = loadTargetResolver;
        this.taperStrategy = taperStrategy;
        this.injuryRiskEvaluator = injuryRiskEvaluator;
        this.injuryPolicyResolver = injuryPolicyResolver;
        this.constraintValidator = constraintValidator;
    }

    public WeekPlanSkeleton planWeek(PlannerInputSnapshot snapshot) {
        PeriodizationResult periodizacao = periodizationPlanner.resolvePhase(snapshot.provas(), snapshot.referenceDate());

        InjuryPolicyResult injuryPolicy = injuryPolicyResolver.resolve(
                snapshot.athlete(), snapshot.referenceDate(), snapshot.injuryRecentWindowDays());

        // Passo 1 da hierarquia P0: lesao ativa/recente tem precedencia sobre a fase da periodizacao.
        TrainingPhase fase = injuryPolicy.phaseOverride().orElse(periodizacao.phase());

        InjuryRiskAssessment risco = injuryRiskEvaluator.assess(
                snapshot.progressaoHistorico(), snapshot.historico(), snapshot.referenceDate());

        WeeklyLoadTarget loadTarget = loadTargetResolver.resolve(fase, snapshot.decisaoProgressao(), snapshot.progressaoHistorico());
        loadTarget = aplicarTaperSeAplicavel(loadTarget, fase, periodizacao, snapshot.referenceDate());

        AthleteConstraints constraints = resolverConstraints(snapshot);
        // SessionSlot prescritivo (dia/TSS por sessao/zonas) e enforcement — fora de escopo desta
        // change (design.md, "Fora de escopo"). Sem sessoes candidatas, a validacao e trivialmente
        // valida; passa a ser exercida de fato quando a geracao de sessoes existir.
        ConstraintValidationResult constraintResult = constraintValidator.validate(constraints, List.of());

        boolean requiresCoachReview = injuryPolicy.requiresCoachReview() || risco.requiresCoachReview();
        // Hierarquia P0: motivo de lesao (passo 1) tem precedencia sobre motivo fisiologico (passo 2).
        String coachReviewReason = injuryPolicy.requiresCoachReview() ? injuryPolicy.reason()
                : risco.requiresCoachReview() ? risco.reason()
                : null;

        String plannerScope = resolverPlannerScope(snapshot.athlete().modalidade());

        return new WeekPlanSkeleton(
                fase,
                loadTarget,
                List.of(),
                risco,
                constraintResult,
                requiresCoachReview,
                coachReviewReason,
                snapshot.referenceDate(),
                plannerScope,
                periodizacao.provaDeterminante());
    }

    private WeeklyLoadTarget aplicarTaperSeAplicavel(WeeklyLoadTarget loadTarget,
                                                       TrainingPhase fase,
                                                       PeriodizationResult periodizacao,
                                                       java.time.LocalDate referenceDate) {
        if (fase != TrainingPhase.TAPER && fase != TrainingPhase.RACE_WEEK) {
            return loadTarget;
        }
        Optional<ProvaSnapshot> provaDeterminante = periodizacao.provaDeterminante();
        if (provaDeterminante.isEmpty() || provaDeterminante.get().distanciaKm() == null) {
            return loadTarget;
        }
        ProvaSnapshot prova = provaDeterminante.get();
        long diasParaProva = ChronoUnit.DAYS.between(referenceDate, prova.dataProva());
        if (!taperStrategy.estaNaJanelaDeTaper(prova.distanciaKm(), diasParaProva)) {
            return loadTarget;
        }
        return taperStrategy.aplicar(loadTarget, diasParaProva);
    }

    private AthleteConstraints resolverConstraints(PlannerInputSnapshot snapshot) {
        return snapshot.onboardingContext()
                .map(OnboardingContext::constraints)
                .orElseGet(() -> new AthleteConstraints(
                        mapDiasDisponiveis(snapshot.athlete().diasDisponiveis()),
                        null,
                        null,
                        List.of()));
    }

    private List<DayOfWeek> mapDiasDisponiveis(List<DiaSemana> diasDisponiveis) {
        if (diasDisponiveis == null) {
            return List.of();
        }
        return diasDisponiveis.stream().map(this::toDayOfWeek).toList();
    }

    private DayOfWeek toDayOfWeek(DiaSemana dia) {
        return switch (dia) {
            case DOMINGO -> DayOfWeek.SUNDAY;
            case SEGUNDA -> DayOfWeek.MONDAY;
            case TERCA -> DayOfWeek.TUESDAY;
            case QUARTA -> DayOfWeek.WEDNESDAY;
            case QUINTA -> DayOfWeek.THURSDAY;
            case SEXTA -> DayOfWeek.FRIDAY;
            case SABADO -> DayOfWeek.SATURDAY;
        };
    }

    private String resolverPlannerScope(String modalidade) {
        if (modalidade == null) {
            return null;
        }
        boolean running = modalidade.equalsIgnoreCase("RUNNING") || modalidade.equalsIgnoreCase("CORRIDA");
        return running ? null : "RUNNING_FIRST";
    }
}
