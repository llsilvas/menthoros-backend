package br.com.menthoros.backend.skills.race;

import br.com.menthoros.backend.skills.race.enums.DataQuality;
import br.com.menthoros.backend.skills.race.enums.ProjectionConfidence;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Orquestra as 3 camadas determinísticas + LLM + Assembler para produzir uma projeção de tempo de prova.
 *
 * Pipeline:
 *   1. Validação de input
 *   2. Camada 1 — PaceRegressionCalculator
 *   3. Camada 2 — RiegelCalculator
 *   4. Camada 3 — PeriodizationAdjuster
 *   5. ConfidenceCalculator
 *   6. RaceProjectionNarrativeGenerator (LLM)
 *   7. RaceProjectionAssembler → RaceProjectionOutput
 *
 * Idempotent: NO — cada execução gera um output diferente (LLM + timestamp).
 * Side Effects: External API call (Anthropic via NarrativeGenerator)
 * Tenant-aware: NO — isolamento garantido pelo serviço chamador.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RaceProjectionSkill {

    private static final long P95_WARNING_THRESHOLD_MS = 2500;

    private final PaceRegressionCalculator regressionCalculator;
    private final RiegelCalculator riegelCalculator;
    private final PeriodizationAdjuster periodizationAdjuster;
    private final ConfidenceCalculator confidenceCalculator;
    private final RaceProjectionNarrativeGenerator narrativeGenerator;
    private final RaceProjectionAssembler assembler;
    private final MeterRegistry meterRegistry;

    /**
     * Executa a pipeline completa de projeção de tempo de prova.
     *
     * @param input input validado com perfil do atleta, histórico, carga e distâncias-alvo
     * @return output completo com projeções, narrativa LLM e metadados
     * @throws IllegalArgumentException se campos obrigatórios do input estiverem ausentes
     */
    public RaceProjectionOutput execute(RaceProjectionInput input) {
        validateInput(input);

        int weeksToRace = resolveWeeksToRace(input);
        log.info("RaceProjectionSkill iniciada: atletaId={}, distâncias={}, weeksToRace={}",
                input.athleteProfile().id(), input.targetDistances(), weeksToRace);

        long totalStart = System.nanoTime();

        // Camada 1 — Regressão de Pace Normalizado
        long t1 = System.nanoTime();
        RegressionResult regression = regressionCalculator.calculate(
                input.trainingHistory(),
                input.athleteProfile().fcMaxima(),
                weeksToRace);
        long regressionMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t1);

        log.debug("Camada 1 concluída: confidence={}, R²={}, sessionsUsed={}, fallback={}, latency={}ms",
                regression.confidenceLayer1(), regression.rSquared(),
                regression.sessionsUsed(), regression.fallbackUsed(), regressionMs);

        // Camada 2 — Conversão por Riegel
        long t2 = System.nanoTime();
        RiegelResult riegel = riegelCalculator.calculate(
                regression,
                input.pastRaces(),
                input.targetDistances());
        long riegelMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t2);

        log.debug("Camada 2 concluída: exponent={}, calibrated={}, anchor={}, latency={}ms",
                riegel.exponentUsed(), riegel.calibrated(), riegel.anchorSource(), riegelMs);

        // Camada 3 — Ajuste por Periodização e TSB
        long t3 = System.nanoTime();
        AdjustmentResult adjustment = periodizationAdjuster.adjust(
                riegel.baseTimesSec(),
                input.loadProjection().plannedPeriodizationPhase(),
                input.loadProjection().projectedTsbOnRaceDay());
        long adjustmentMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t3);

        log.debug("Camada 3 concluída: phase={}, factor={}, latency={}ms",
                adjustment.adjustmentRationaleKey(), adjustment.adjustmentFactor(), adjustmentMs);

        // Confiança global
        ProjectionConfidence overall = confidenceCalculator.calculate(
                regression.confidenceLayer1(), riegel.calibrated());

        // Contexto para o LLM (sem PII sensível — apenas dados fisiológicos e métricas)
        long tLlm = System.nanoTime();
        Map<String, Object> llmContext = buildLlmContext(input, regression, riegel, adjustment, weeksToRace);
        RaceProjectionNarrativeGenerator.NarrativeOutput narrative = narrativeGenerator.generate(llmContext);
        long llmMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - tLlm);

        // Enriquecer key_assumptions com flags de dados ausentes detectados na pipeline
        narrative = enrichAssumptions(narrative, regression);

        RaceProjectionOutput output = assembler.assemble(
                adjustment, riegel, regression,
                input.loadProjection(),
                input.pastRaces() != null ? input.pastRaces() : List.of(),
                input.coachGoalOverride(),
                narrative,
                overall,
                weeksToRace);

        long totalMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - totalStart);

        recordMetrics(overall, regressionMs, riegelMs, adjustmentMs, llmMs, totalMs);

        log.info("RaceProjectionSkill concluída: atletaId={}, confidence={}, distâncias={}, totalMs={}",
                input.athleteProfile().id(), overall, output.projections().keySet(), totalMs);

        if (totalMs > P95_WARNING_THRESHOLD_MS) {
            log.warn("RaceProjectionSkill P95 excedido: totalMs={}, regression={}ms, riegel={}ms, adjustment={}ms, llm={}ms",
                    totalMs, regressionMs, riegelMs, adjustmentMs, llmMs);
        }

        return output;
    }

    private void recordMetrics(ProjectionConfidence confidence,
                                long regressionMs, long riegelMs, long adjustmentMs,
                                long llmMs, long totalMs) {
        Counter.builder("race_projection_executions_total")
                .tag("confidence", confidence.name())
                .register(meterRegistry)
                .increment();

        Timer.builder("race_projection_duration_ms")
                .tag("layer", "regression")
                .register(meterRegistry)
                .record(regressionMs, TimeUnit.MILLISECONDS);

        Timer.builder("race_projection_duration_ms")
                .tag("layer", "riegel")
                .register(meterRegistry)
                .record(riegelMs, TimeUnit.MILLISECONDS);

        Timer.builder("race_projection_duration_ms")
                .tag("layer", "adjustment")
                .register(meterRegistry)
                .record(adjustmentMs, TimeUnit.MILLISECONDS);

        Timer.builder("race_projection_duration_ms")
                .tag("layer", "llm")
                .register(meterRegistry)
                .record(llmMs, TimeUnit.MILLISECONDS);

        Timer.builder("race_projection_duration_ms")
                .tag("layer", "total")
                .register(meterRegistry)
                .record(totalMs, TimeUnit.MILLISECONDS);
    }

    // --- validação ---

    private void validateInput(RaceProjectionInput input) {
        if (input == null) throw new IllegalArgumentException("RaceProjectionInput cannot be null");
        if (input.athleteProfile() == null) throw new IllegalArgumentException("AthleteProfile is required");
        if (input.trainingHistory() == null) throw new IllegalArgumentException("TrainingHistory is required");
        if (input.loadProjection() == null) throw new IllegalArgumentException("LoadProjection is required");
        if (input.targetDistances() == null || input.targetDistances().isEmpty()) {
            throw new IllegalArgumentException("At least one target distance is required");
        }
        if (input.trainingHistory().dataQuality() == DataQuality.SPARSE) {
            log.warn("TrainingHistory com qualidade SPARSE para atletaId={} — projeção com confiança reduzida",
                    input.athleteProfile().id());
        }
    }

    // --- resolução de semanas ---

    private int resolveWeeksToRace(RaceProjectionInput input) {
        if (input.weeksToRaceOverride() != null && input.weeksToRaceOverride() > 0) {
            return input.weeksToRaceOverride();
        }
        return input.loadProjection().weeksToRace() != null
                ? input.loadProjection().weeksToRace()
                : 4;
    }

    // --- enriquecimento de premissas ---

    private RaceProjectionNarrativeGenerator.NarrativeOutput enrichAssumptions(
            RaceProjectionNarrativeGenerator.NarrativeOutput narrative,
            RegressionResult regression) {

        if (regression.hrMissingAssumption() == null) return narrative;

        // Injeta a premissa de FC ausente no início da lista se não estiver já presente
        List<String> enriched = new ArrayList<>(narrative.keyAssumptions());
        if (enriched.stream().noneMatch(a -> a.toLowerCase().contains("fc") || a.toLowerCase().contains("frequência"))) {
            enriched.add(0, regression.hrMissingAssumption());
        }

        int maxAssumptions = 5;
        return new RaceProjectionNarrativeGenerator.NarrativeOutput(
                narrative.progressionNarrative(),
                enriched.stream().limit(maxAssumptions).toList(),
                narrative.coachNote()
        );
    }

    // --- contexto LLM ---

    private Map<String, Object> buildLlmContext(RaceProjectionInput input,
                                                  RegressionResult regression,
                                                  RiegelResult riegel,
                                                  AdjustmentResult adjustment,
                                                  int weeksToRace) {
        Map<String, Object> ctx = new HashMap<>();

        Map<String, Object> athleteCtx = new HashMap<>();
        athleteCtx.put("nome", input.athleteProfile().nome());
        athleteCtx.put("nivel_experiencia", input.athleteProfile().nivelExperiencia().name());
        athleteCtx.put("fc_maxima", input.athleteProfile().fcMaxima());
        athleteCtx.put("fc_limiar", input.athleteProfile().fcLimiar());
        ctx.put("athlete", athleteCtx);

        Map<String, Object> regressionCtx = new HashMap<>();
        regressionCtx.put("slope", regression.slope());
        regressionCtx.put("r_squared", regression.rSquared());
        regressionCtx.put("sessions_used", regression.sessionsUsed());
        regressionCtx.put("fallback_used", regression.fallbackUsed());
        regressionCtx.put("confidence_layer1", regression.confidenceLayer1().name());
        ctx.put("regression", regressionCtx);

        ctx.put("periodization", Map.of(
                "adjustment_rationale_key", adjustment.adjustmentRationaleKey(),
                "adjustment_factor", adjustment.adjustmentFactor()
        ));

        ctx.put("ctl_forecast", Map.of(
                "current_ctl", input.loadProjection().currentCtl(),
                "projected_ctl_race_day", input.loadProjection().projectedCtlOnRaceDay(),
                "weeks_to_race", weeksToRace
        ));

        ctx.put("weeks_to_race", weeksToRace);

        return ctx;
    }
}
