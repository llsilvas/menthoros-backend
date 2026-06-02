package br.com.menthoros.backend.skills.analysis;

import br.com.menthoros.backend.skills.core.DomainSkill;
import br.com.menthoros.backend.skills.core.SkillCategory;
import br.com.menthoros.backend.skills.core.SkillConfidence;
import br.com.menthoros.backend.skills.core.SkillContext;
import br.com.menthoros.backend.skills.core.SkillResult;
import br.com.menthoros.backend.skills.core.SkillSeverity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Skill de análise de treino intervalado.
 *
 * <p>Calcula o Intensity Factor (IF) do treino e classifica a qualidade de execução.
 * Prioriza dados de {@link EtapaRealizadaResumo} quando disponíveis (calculando IF
 * médio das etapas PRINCIPAL); sem etapas, usa {@code fcMediaGlobal / fcLimiar}
 * como fallback automático.</p>
 *
 * <h2>Classificação por IF</h2>
 * <ul>
 *   <li>IF >= 1.05 → EXCELENTE (tiros acima do alvo)</li>
 *   <li>0.95 <= IF < 1.05 → BOA (dentro do alvo)</li>
 *   <li>0.85 <= IF < 0.95 → REGULAR (abaixo do alvo)</li>
 *   <li>IF < 0.85 → FRACA (muito abaixo do alvo)</li>
 * </ul>
 *
 * <p><b>Idempotente:</b> SIM — análise determinística, sem mutação de estado.</p>
 * <p><b>Side Effects:</b> NONE</p>
 * <p><b>Tenant-aware:</b> SIM — context.tenantId() disponível via {@link SkillContext}.</p>
 */
@Slf4j
@Component
public class IntervalWorkoutAnalysisSkill
        implements DomainSkill<IntervalWorkoutAnalysisInput, IntervalWorkoutAnalysisPayload> {

    private static final String SKILL_KEY     = "interval-workout-analysis-v1";
    private static final String SKILL_VERSION = "1.0";

    private static final double IF_EXCELENTE_MIN = 1.05;
    private static final double IF_BOA_MIN       = 0.95;
    private static final double IF_REGULAR_MIN   = 0.85;

    private static final String TIPO_ETAPA_PRINCIPAL = "PRINCIPAL";

    @Override
    public String skillKey() {
        return SKILL_KEY;
    }

    @Override
    public String skillVersion() {
        return SKILL_VERSION;
    }

    @Override
    public SkillCategory category() {
        return SkillCategory.WORKOUT_ANALYSIS;
    }

    /**
     * Executa a análise do treino intervalado.
     *
     * <p><b>Idempotente:</b> SIM — mesmos inputs produzem sempre o mesmo resultado.</p>
     * <p><b>Side Effects:</b> NONE</p>
     * <p><b>Tenant-aware:</b> SIM</p>
     *
     * @param input   dados do treino intervalado realizado (sem entidades JPA)
     * @param context contexto de execução com atletaId, tenantId e dataReferencia
     * @return resultado estruturado com payload, evidências e recomendações
     * @throws IllegalArgumentException se {@code input} for {@code null}
     */
    @Override
    public SkillResult<IntervalWorkoutAnalysisPayload> execute(
            IntervalWorkoutAnalysisInput input,
            SkillContext context) {

        if (input == null) {
            throw new IllegalArgumentException("IntervalWorkoutAnalysisInput cannot be null");
        }

        log.debug("IntervalWorkoutAnalysisSkill iniciada: atletaId={}, tenantId={}, tipo={}, distanciaKm={}",
                context.atletaId(), context.tenantId(), input.tipoTreino(), input.distanciaKm());

        List<EtapaRealizadaResumo> etapasPrincipais = etapasPrincipais(input);
        boolean usouEtapas = !etapasPrincipais.isEmpty();

        double ifMedio = usouEtapas
                ? calcularIfMedioEtapas(etapasPrincipais, input.fcLimiar())
                : input.fcMediaGlobal() / input.fcLimiar();

        String qualidade = classificarQualidade(ifMedio);
        String interpretacao = gerarInterpretacao(ifMedio, qualidade, usouEtapas);

        List<String> evidence  = montarEvidencias(input, ifMedio, usouEtapas, etapasPrincipais.size());
        List<String> recommendations = montarRecomendacoes(qualidade, ifMedio);

        SkillSeverity severity  = severidadePorQualidade(qualidade);
        SkillConfidence confidence = usouEtapas ? SkillConfidence.HIGH : SkillConfidence.MEDIUM;

        IntervalWorkoutAnalysisPayload payload =
                new IntervalWorkoutAnalysisPayload(ifMedio, qualidade, usouEtapas, interpretacao);

        log.info("IntervalWorkoutAnalysisSkill concluída: atletaId={}, ifMedio={}, qualidade={}, usouEtapas={}",
                context.atletaId(), String.format("%.3f", ifMedio), qualidade, usouEtapas);

        return new SkillResult<>(
                SKILL_KEY,
                SKILL_VERSION,
                severity,
                confidence,
                payload,
                List.copyOf(evidence),
                List.copyOf(recommendations));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cálculo de IF
    // ─────────────────────────────────────────────────────────────────────────

    private List<EtapaRealizadaResumo> etapasPrincipais(IntervalWorkoutAnalysisInput input) {
        if (input.etapas() == null || input.etapas().isEmpty()) {
            return List.of();
        }
        return input.etapas().stream()
                .filter(e -> TIPO_ETAPA_PRINCIPAL.equalsIgnoreCase(e.tipoEtapa()))
                .toList();
    }

    private double calcularIfMedioEtapas(List<EtapaRealizadaResumo> principais, double fcLimiar) {
        return principais.stream()
                .mapToDouble(e -> e.fcMedia() / fcLimiar)
                .average()
                .orElse(0.0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Classificação e interpretação
    // ─────────────────────────────────────────────────────────────────────────

    private String classificarQualidade(double ifMedio) {
        if (ifMedio >= IF_EXCELENTE_MIN) return "EXCELENTE";
        if (ifMedio >= IF_BOA_MIN)       return "BOA";
        if (ifMedio >= IF_REGULAR_MIN)   return "REGULAR";
        return "FRACA";
    }

    private String gerarInterpretacao(double ifMedio, String qualidade, boolean usouEtapas) {
        String origem = usouEtapas ? "média das etapas principais" : "FC global (fallback)";
        String ifFmt  = String.format("%.2f", ifMedio);
        return switch (qualidade) {
            case "EXCELENTE" -> String.format("Tiros executados acima do alvo — IF %.2f (%s).", ifMedio, origem);
            case "BOA"       -> String.format("Execução dentro do alvo — IF %s (%s).", ifFmt, origem);
            case "REGULAR"   -> String.format("Intensidade abaixo do alvo — IF %s (%s). Verificar prescrição.", ifFmt, origem);
            default          -> String.format("Intensidade muito abaixo do alvo — IF %s (%s). Possível falta de esforço ou erro de dados.", ifFmt, origem);
        };
    }

    private SkillSeverity severidadePorQualidade(String qualidade) {
        return switch (qualidade) {
            case "EXCELENTE", "BOA" -> SkillSeverity.INFO;
            case "REGULAR"          -> SkillSeverity.WARNING;
            default                 -> SkillSeverity.WARNING;
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Evidências e recomendações
    // ─────────────────────────────────────────────────────────────────────────

    private List<String> montarEvidencias(IntervalWorkoutAnalysisInput input, double ifMedio,
                                          boolean usouEtapas, int numEtapasPrincipais) {
        List<String> ev = new ArrayList<>();
        ev.add(String.format("tipoTreino=%s", input.tipoTreino()));
        ev.add(String.format("distanciaKm=%.1f", input.distanciaKm()));
        ev.add(String.format("duracaoMin=%d", input.duracaoMin()));
        ev.add(String.format("tssRealizado=%.1f", input.tssRealizado()));
        ev.add(String.format("percepcaoEsforco=%d", input.percepcaoEsforco()));
        ev.add(String.format("fcMediaGlobal=%.1f", input.fcMediaGlobal()));
        ev.add(String.format("fcLimiar=%.1f", input.fcLimiar()));
        ev.add(String.format("ifMedio=%.3f", ifMedio));
        ev.add(String.format("metodo=%s", usouEtapas
                ? "etapas_principais (" + numEtapasPrincipais + " etapas)"
                : "fc_global_fallback"));
        return ev;
    }

    private List<String> montarRecomendacoes(String qualidade, double ifMedio) {
        List<String> rec = new ArrayList<>();
        switch (qualidade) {
            case "EXCELENTE" -> {
                rec.add("Tiros bem executados acima do limiar. Verificar se esforço está controlado a longo prazo.");
                rec.add("Monitorar RPE e qualidade do sono nas próximas 48h.");
            }
            case "BOA" -> rec.add("Execução dentro do alvo. Manter a prescrição atual.");
            case "REGULAR" -> {
                rec.add("Intensidade aquém do alvo. Revisar condições do treino (temperatura, terreno, fadiga).");
                rec.add(String.format("IF %.2f está abaixo do alvo de %.2f. Considerar ajuste de prescrição.", ifMedio, IF_BOA_MIN));
            }
            default -> {
                rec.add("Intensidade muito abaixo do alvo. Verificar motivação, condições ou erros de monitoramento.");
                rec.add("Revisar prescrição com o atleta antes do próximo treino intervalado.");
            }
        }
        return rec;
    }
}
