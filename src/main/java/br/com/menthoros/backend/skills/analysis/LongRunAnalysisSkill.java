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
 * Skill de análise de long run (corrida longa).
 *
 * <p>Avalia a qualidade aeróbia do long run com base em drift de FC e progressão de pace.
 * Prioriza dados de {@link EtapaRealizadaResumo} quando disponíveis, calculando:</p>
 * <ul>
 *   <li><b>Drift de FC:</b> variação percentual da FC entre o primeiro e o último terço das etapas.
 *       Drift > 10% indica fadiga aeróbia e resulta em qualidade BAIXA.</li>
 *   <li><b>Progressão de pace:</b> o pace médio do último terço é comparado com o do primeiro;
 *       pace menor (mais rápido) no último terço indica progressão correta.</li>
 * </ul>
 *
 * <p>Sem etapas, usa IF global ({@code fcMediaGlobal / fcLimiar}) e RPE para classificar:</p>
 * <ul>
 *   <li>IF &lt; 0.95 E RPE &lt;= 7 → qualidade ALTA</li>
 *   <li>IF >= 0.95 E RPE >= 8 → qualidade BAIXA</li>
 *   <li>Outros → qualidade MODERADA</li>
 * </ul>
 *
 * <p><b>Idempotente:</b> SIM — análise determinística, sem mutação de estado.</p>
 * <p><b>Side Effects:</b> NONE</p>
 * <p><b>Tenant-aware:</b> SIM — context.tenantId() disponível via {@link SkillContext}.</p>
 */
@Slf4j
@Component
public class LongRunAnalysisSkill
        implements DomainSkill<LongRunAnalysisInput, LongRunAnalysisPayload> {

    private static final String SKILL_KEY     = "long-run-analysis-v1";
    private static final String SKILL_VERSION = "1.0";

    /** Threshold de drift de FC (%) acima do qual a qualidade aeróbia é BAIXA. */
    private static final double DRIFT_FC_LIMITE = 10.0;

    /** IF global acima deste valor combinado com RPE >= 8 indica qualidade BAIXA no fallback. */
    private static final double IF_ALTO = 0.95;

    /** RPE a partir do qual, combinado com IF alto, o fallback classifica qualidade BAIXA. */
    private static final int RPE_ALTO = 8;

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
     * Executa a análise do long run.
     *
     * <p><b>Idempotente:</b> SIM — mesmos inputs produzem sempre o mesmo resultado.</p>
     * <p><b>Side Effects:</b> NONE</p>
     * <p><b>Tenant-aware:</b> SIM</p>
     *
     * @param input   dados do long run realizado (sem entidades JPA)
     * @param context contexto de execução com atletaId, tenantId e dataReferencia
     * @return resultado estruturado com payload, evidências e recomendações
     * @throws IllegalArgumentException se {@code input} for {@code null}
     */
    @Override
    public SkillResult<LongRunAnalysisPayload> execute(LongRunAnalysisInput input, SkillContext context) {

        if (input == null) {
            throw new IllegalArgumentException("LongRunAnalysisInput cannot be null");
        }

        log.debug("LongRunAnalysisSkill iniciada: atletaId={}, tenantId={}, distanciaKm={}, duracaoMin={}",
                context.atletaId(), context.tenantId(), input.distanciaKm(), input.duracaoMin());

        List<EtapaRealizadaResumo> etapas = etapasValidas(input);
        boolean usouEtapas = !etapas.isEmpty();

        double driftFc;
        boolean progressaoCorreta;
        String qualidadeAerobia;

        if (usouEtapas) {
            driftFc          = calcularDriftFc(etapas);
            progressaoCorreta = calcularProgressaoPace(etapas);
            qualidadeAerobia  = classificarComEtapas(driftFc, progressaoCorreta);
        } else {
            driftFc          = 0.0;
            progressaoCorreta = calcularProgressaoFallback(input);
            qualidadeAerobia  = classificarFallback(input);
        }

        String interpretacao = gerarInterpretacao(driftFc, progressaoCorreta, qualidadeAerobia, usouEtapas);
        List<String> evidence      = montarEvidencias(input, driftFc, progressaoCorreta, qualidadeAerobia, usouEtapas);
        List<String> recommendations = montarRecomendacoes(qualidadeAerobia, driftFc, progressaoCorreta);

        SkillSeverity severity    = severidadePorQualidade(qualidadeAerobia);
        SkillConfidence confidence = usouEtapas ? SkillConfidence.HIGH : SkillConfidence.MEDIUM;

        LongRunAnalysisPayload payload =
                new LongRunAnalysisPayload(driftFc, progressaoCorreta, qualidadeAerobia, interpretacao);

        log.info("LongRunAnalysisSkill concluída: atletaId={}, drift={}%, progressao={}, qualidade={}, usouEtapas={}",
                context.atletaId(), String.format("%.1f", driftFc), progressaoCorreta, qualidadeAerobia, usouEtapas);

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
    // Cálculo com etapas
    // ─────────────────────────────────────────────────────────────────────────

    private List<EtapaRealizadaResumo> etapasValidas(LongRunAnalysisInput input) {
        if (input.etapas() == null || input.etapas().isEmpty()) {
            return List.of();
        }
        return List.copyOf(input.etapas());
    }

    /**
     * Calcula o drift de FC (%) entre a FC média do primeiro terço e a do último terço das etapas.
     *
     * @param etapas lista de etapas não vazia
     * @return drift percentual (positivo quando FC aumentou)
     */
    private double calcularDriftFc(List<EtapaRealizadaResumo> etapas) {
        int n          = etapas.size();
        int tamanhoTerco = Math.max(1, n / 3);

        List<EtapaRealizadaResumo> primeiroTerco = etapas.subList(0, tamanhoTerco);
        List<EtapaRealizadaResumo> ultimoTerco   = etapas.subList(n - tamanhoTerco, n);

        double fcPrimeiro = mediaFc(primeiroTerco);
        double fcUltimo   = mediaFc(ultimoTerco);

        if (fcPrimeiro == 0.0) return 0.0;
        return ((fcUltimo - fcPrimeiro) / fcPrimeiro) * 100.0;
    }

    private double mediaFc(List<EtapaRealizadaResumo> etapas) {
        return etapas.stream()
                .mapToDouble(EtapaRealizadaResumo::fcMedia)
                .average()
                .orElse(0.0);
    }

    /**
     * Verifica se o pace médio do último terço é menor ou igual (mais rápido) ao do primeiro terço.
     *
     * @param etapas lista de etapas não vazia
     * @return {@code true} se o pace melhorou ou foi mantido no último terço
     */
    private boolean calcularProgressaoPace(List<EtapaRealizadaResumo> etapas) {
        int n          = etapas.size();
        int tamanhoTerco = Math.max(1, n / 3);

        List<EtapaRealizadaResumo> primeiroTerco = etapas.subList(0, tamanhoTerco);
        List<EtapaRealizadaResumo> ultimoTerco   = etapas.subList(n - tamanhoTerco, n);

        double pacePrimeiro = mediaPace(primeiroTerco);
        double paceUltimo   = mediaPace(ultimoTerco);

        // pace em sec/km: menor = mais rápido → progressão correta quando último terço <= primeiro terço
        return paceUltimo <= pacePrimeiro;
    }

    private double mediaPace(List<EtapaRealizadaResumo> etapas) {
        return etapas.stream()
                .mapToDouble(EtapaRealizadaResumo::paceMedia)
                .average()
                .orElse(0.0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Fallback (sem etapas)
    // ─────────────────────────────────────────────────────────────────────────

    private boolean calcularProgressaoFallback(LongRunAnalysisInput input) {
        // Sem etapas não é possível calcular progressão real; considera correta se pace global <= alvo
        return input.paceMediaGlobal() <= input.paceAlvo();
    }

    /**
     * Classifica qualidade aeróbia no fallback (sem etapas) usando IF global e RPE.
     *
     * <ul>
     *   <li>IF >= {@value IF_ALTO} E RPE >= {@value RPE_ALTO} → BAIXA (esforço alto demais para long run)</li>
     *   <li>IF < {@value IF_ALTO} E RPE <= 7 → ALTA (esforço controlado)</li>
     *   <li>Outros → MODERADA</li>
     * </ul>
     */
    private String classificarFallback(LongRunAnalysisInput input) {
        double ifGlobal = input.fcLimiar() > 0 ? input.fcMediaGlobal() / input.fcLimiar() : 0.0;
        int rpe = input.percepcaoEsforco();

        if (ifGlobal >= IF_ALTO && rpe >= RPE_ALTO) return "BAIXA";
        if (ifGlobal < IF_ALTO && rpe <= 7)         return "ALTA";
        return "MODERADA";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Classificação com etapas
    // ─────────────────────────────────────────────────────────────────────────

    private String classificarComEtapas(double driftFc, boolean progressaoCorreta) {
        if (driftFc > DRIFT_FC_LIMITE) return "BAIXA";
        if (progressaoCorreta)         return "ALTA";
        return "MODERADA";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Interpretação
    // ─────────────────────────────────────────────────────────────────────────

    private String gerarInterpretacao(double driftFc, boolean progressaoCorreta,
                                      String qualidadeAerobia, boolean usouEtapas) {
        if (!usouEtapas) {
            return switch (qualidadeAerobia) {
                case "ALTA"     -> "Long run em ritmo aeróbio controlado — IF e RPE indicam boa base aeróbia (fallback).";
                case "BAIXA"    -> "Esforço excessivo para um long run — IF e/ou RPE acima do ideal (fallback). Revisar pace alvo.";
                default         -> "Long run em zona de transição aeróbia (fallback). Monitorar evolução.";
            };
        }

        return switch (qualidadeAerobia) {
            case "ALTA"  -> String.format(
                    "Long run executado com excelente controle aeróbio — drift FC %.1f%%, progressão de pace: %s.",
                    driftFc, progressaoCorreta ? "correta" : "não confirmada");
            case "BAIXA" -> String.format(
                    "Fadiga aeróbia detectada — drift FC %.1f%% (limite: %.0f%%). Pode indicar saída rápida demais.",
                    driftFc, DRIFT_FC_LIMITE);
            default      -> String.format(
                    "Long run em zona moderada — drift FC %.1f%%, progressão: %s. Analisar distribuição de esforço.",
                    driftFc, progressaoCorreta ? "correta" : "com queda de rendimento");
        };
    }

    private SkillSeverity severidadePorQualidade(String qualidadeAerobia) {
        return switch (qualidadeAerobia) {
            case "ALTA"     -> SkillSeverity.INFO;
            case "MODERADA" -> SkillSeverity.INFO;
            default         -> SkillSeverity.WARNING;
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Evidências e recomendações
    // ─────────────────────────────────────────────────────────────────────────

    private List<String> montarEvidencias(LongRunAnalysisInput input, double driftFc,
                                          boolean progressaoCorreta, String qualidadeAerobia,
                                          boolean usouEtapas) {
        List<String> ev = new ArrayList<>();
        ev.add(String.format("distanciaKm=%.1f", input.distanciaKm()));
        ev.add(String.format("duracaoMin=%d", input.duracaoMin()));
        ev.add(String.format("tssRealizado=%.1f", input.tssRealizado()));
        ev.add(String.format("percepcaoEsforco=%d", input.percepcaoEsforco()));
        ev.add(String.format("fcMediaGlobal=%.1f", input.fcMediaGlobal()));
        ev.add(String.format("fcLimiar=%.1f", input.fcLimiar()));
        ev.add(String.format("paceMediaGlobal=%.1f sec/km", input.paceMediaGlobal()));
        ev.add(String.format("paceAlvo=%.1f sec/km", input.paceAlvo()));
        ev.add(String.format("metodo=%s", usouEtapas ? "etapas_calculadas" : "fallback_global"));
        ev.add(String.format("driftFc=%.2f%%", driftFc));
        ev.add(String.format("progressaoCorreta=%b", progressaoCorreta));
        ev.add(String.format("qualidadeAerobia=%s", qualidadeAerobia));
        return ev;
    }

    private List<String> montarRecomendacoes(String qualidadeAerobia, double driftFc, boolean progressaoCorreta) {
        List<String> rec = new ArrayList<>();
        switch (qualidadeAerobia) {
            case "ALTA" -> {
                rec.add("Long run executado com controle aeróbio adequado. Manter prescrição.");
                if (progressaoCorreta) {
                    rec.add("Progressão de pace negativa (negative split) confirma boa gestão de esforço.");
                }
            }
            case "BAIXA" -> {
                if (driftFc > DRIFT_FC_LIMITE) {
                    rec.add(String.format(
                            "Drift de FC %.1f%% indica saída rápida demais. Considerar redução de 5-10%% no pace dos próximos long runs.",
                            driftFc));
                }
                rec.add("Revisar estratégia de pace para o próximo long run — priorizar ritmo controlado nos primeiros terços.");
            }
            default -> {
                rec.add("Long run em zona moderada. Avaliar se pace alvo está calibrado corretamente.");
                if (!progressaoCorreta) {
                    rec.add("Queda de rendimento no último terço detectada. Verificar nutrição e hidratação durante o treino.");
                }
            }
        }
        return rec;
    }
}
