package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.domain.compliance.PlannerViolation;
import br.com.menthoros.backend.domain.planner.WeekPlanSkeleton;
import br.com.menthoros.backend.dto.llm.PlanoSemanalLlmDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.services.prompt.constraint.Constraint;
import br.com.menthoros.backend.services.quality.PlanQualityChecker;
import br.com.menthoros.backend.services.quality.ViolacaoQualidade;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.List;

/**
 * Grader determinístico do eval set (plan-generation-eval-set, fatia 1) — roda **só em modo
 * candidato** (design.md "Correção de escopo", rodada 2 de DoR): fixtures de auditoria não têm
 * `constraintsAtivas`/`skeleton` retroativos, então este grader nunca as recebe delas.
 *
 * <p>Despacha por {@code schemaVersion}: {@code schema-v2} passa por {@link SessionResolver}
 * antes de checar (mesmo caminho de {@code IaServiceImpl.gerarChamadaLlmV2}); qualquer outro valor
 * (ou ausente) desserializa direto para {@link PlanoSemanalLlmDto}.
 *
 * <p>Usa um {@link SimpleMeterRegistry} descartável para {@link PlanQualityChecker} — nunca o
 * registry de produção, para não poluir métricas com execuções de eval (design.md §1.4).
 *
 * <p>Idempotent: YES — leitura pura. Side Effects: NONE (métrica só no registry descartável
 * interno). Tenant-aware: NÃO — opera sobre dados de fixture/candidato, não de tenant real.
 *
 * <p>Deliberadamente sem {@code @Component} — nunca instanciada pelo Spring (achado do /qa).
 */
public class EvalDeterministicGrader {

    private final ObjectMapper objectMapper;
    private final SessionResolver sessionResolver;
    private final PlannerShadowService plannerShadowService;
    private final PlanQualityChecker planQualityChecker;

    public EvalDeterministicGrader(ObjectMapper objectMapper, SessionResolver sessionResolver,
                                    PlannerShadowService plannerShadowService) {
        this.objectMapper = objectMapper;
        this.sessionResolver = sessionResolver;
        this.plannerShadowService = plannerShadowService;
        this.planQualityChecker = new PlanQualityChecker(new SimpleMeterRegistry());
    }

    /** Violações dos dois checkers reaproveitados — tipos distintos, sem conversão forçada. */
    public record Resultado(List<ViolacaoQualidade> violacoesQualidade,
                             List<PlannerViolation> violacoesCompliance) {
    }

    /**
     * Avalia uma resposta candidata (JSON bruto do LLM, v1 ou v2) contra as regras/skeleton da
     * fixture de candidato. Idempotent: YES. Side Effects: NONE. Tenant-aware: NÃO.
     */
    public Resultado avaliar(String respostaJson, @Nullable String schemaVersion, List<Constraint> regras,
                              @Nullable WeekPlanSkeleton skeleton, AthleteZones zonasAtleta,
                              Atleta atleta, LocalDate semanaInicio) {
        PlanoSemanalLlmDto plano = EvalPlanoJsonParser.parsear(objectMapper, sessionResolver, respostaJson,
                schemaVersion, zonasAtleta);
        List<ViolacaoQualidade> violacoesQualidade = planQualityChecker.check(plano, regras);
        List<PlannerViolation> violacoesCompliance = skeleton != null
                ? plannerShadowService.checkPreRedistribution(plano, skeleton, atleta, semanaInicio)
                : List.of();
        return new Resultado(violacoesQualidade, violacoesCompliance);
    }
}
