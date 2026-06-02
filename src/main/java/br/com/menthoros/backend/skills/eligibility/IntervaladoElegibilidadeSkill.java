package br.com.menthoros.backend.skills.eligibility;

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
import java.util.Set;

/**
 * Skill determinística de elegibilidade para treinos intervalados.
 *
 * <p>Avalia 5 portões de decisão sequencialmente para determinar se um atleta
 * está apto a realizar treino de alta intensidade (intervalado) na semana corrente.
 * O resultado é estruturado e pode ser injetado no prompt do LLM pelo
 * {@code PlanoTreinoPromptBuilder} — o LLM não pode contrariar a decisão.</p>
 *
 * <h2>Portões avaliados</h2>
 * <ol>
 *   <li><b>Lesão ativa</b> — bloqueador absoluto → {@code BLOCKER}</li>
 *   <li><b>TSB de prontidão</b> — abaixo de {@code -20.0} bloqueia → {@code WARNING}</li>
 *   <li><b>Fase de periodização</b> — TAPER e RECOVERY bloqueiam → {@code WARNING}</li>
 *   <li><b>Recuperação mínima</b> — menos de 2 dias desde último intervalado → {@code WARNING}</li>
 *   <li><b>Ramp rate</b> — acima de {@code 8.0} semanal → {@code WARNING}</li>
 * </ol>
 *
 * <p><b>Idempotent:</b> YES — Análise pura sobre dados de entrada; sem side effects.</p>
 * <p><b>Side Effects:</b> NONE</p>
 * <p><b>Tenant-aware:</b> YES — {@code context.tenantId()} disponível, utilizado no log.</p>
 */
@Slf4j
@Component
public class IntervaladoElegibilidadeSkill
        implements DomainSkill<IntervaladoElegibilidadeInput, IntervaladoElegibilidadePayload> {

    /** Identificador único imutável da skill no registry. */
    private static final String SKILL_KEY     = "intervalado-elegibilidade-v1";
    private static final String SKILL_VERSION = "1.0";

    /**
     * Threshold de TSB de prontidão abaixo do qual o atleta não deve realizar intervalado.
     * Abaixo desse valor o atleta está em fadiga acumulada relevante.
     */
    private static final double TSB_MINIMO_ELEGIBILIDADE = -20.0;

    /**
     * Threshold de ramp rate semanal (incremento de CTL) acima do qual o risco de
     * overtraining é considerado elevado e intervalados são contraindicados.
     */
    private static final double RAMP_RATE_LIMITE = 8.0;

    /**
     * Mínimo de dias desde o último treino de alta intensidade para nova sessão ser segura.
     */
    private static final int DIAS_RECUPERACAO_MINIMO = 2;

    /**
     * Fases de periodização que bloqueiam qualquer treino intervalado.
     * Em TAPER, o objetivo é chegar à prova recuperado.
     * Em RECOVERY (pós-prova), o sistema músculo-esquelético ainda está sob stress.
     */
    private static final Set<String> FASES_BLOQUEADORAS = Set.of("TAPER", "RECOVERY", "SEMANA_PROVA", "POS_PROVA");

    // ─────────────────────────────────────────────────────────────────────────
    // Contrato DomainSkill
    // ─────────────────────────────────────────────────────────────────────────

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
        return SkillCategory.ELIGIBILITY;
    }

    /**
     * Executa a avaliação de elegibilidade para treino intervalado.
     *
     * <p><b>Idempotent:</b> YES — operação de leitura pura, sem mutação de estado.</p>
     * <p><b>Side Effects:</b> NONE</p>
     * <p><b>Tenant-aware:</b> YES</p>
     *
     * @param input   dados fisiológicos e de contexto do atleta (sem entidades JPA)
     * @param context contexto de execução com atletaId, tenantId e dataReferencia
     * @return {@link SkillResult} com payload de elegibilidade, evidências e recomendações
     * @throws IllegalArgumentException se {@code input} for {@code null}
     */
    @Override
    public SkillResult<IntervaladoElegibilidadePayload> execute(
            IntervaladoElegibilidadeInput input,
            SkillContext context) {

        if (input == null) {
            throw new IllegalArgumentException("IntervaladoElegibilidadeInput cannot be null");
        }

        log.debug("IntervaladoElegibilidadeSkill iniciada: atletaId={}, tenantId={}, tsb={}, ctl={}, fase={}",
                context.atletaId(), context.tenantId(),
                input.tsbProntidaoAtual(), input.ctlAtual(), input.fasePeriodizacao());

        List<String> condicoes  = new ArrayList<>();
        List<String> evidence   = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();

        // ── PORTÃO 1: Lesão ativa — BLOCKER absoluto ─────────────────────────

        if (input.temLesaoAtiva()) {
            condicoes.add("Lesão ativa detectada — intervalado proibido");
            evidence.add("temLesaoAtiva=true");
            recommendations.add("Prescrever apenas treinos REGENERATIVO ou CONTINUO leve em Z1-Z2 até liberação médica.");

            log.warn("IntervaladoElegibilidadeSkill BLOCKER: atletaId={} tem lesão ativa",
                    context.atletaId());

            return buildResult(
                    SkillSeverity.BLOCKER,
                    SkillConfidence.HIGH,
                    false,
                    "Lesão ativa: intervalado proibido",
                    condicoes, evidence, recommendations);
        }
        condicoes.add("Sem lesão ativa — portão 1 passou");
        evidence.add("temLesaoAtiva=false");

        // ── PORTÃO 2: TSB de prontidão ───────────────────────────────────────

        if (input.tsbProntidaoAtual() < TSB_MINIMO_ELEGIBILIDADE) {
            String tsbStr = String.format("%.1f", input.tsbProntidaoAtual());
            condicoes.add("TSB=" + tsbStr + " abaixo do mínimo de " + TSB_MINIMO_ELEGIBILIDADE + " — portão 2 bloqueado");
            evidence.add("tsbProntidaoAtual=" + tsbStr + " < limiar=" + TSB_MINIMO_ELEGIBILIDADE);
            recommendations.add("Reduzir volume e intensidade até TSB > " + TSB_MINIMO_ELEGIBILIDADE + ".");

            log.warn("IntervaladoElegibilidadeSkill WARNING (TSB): atletaId={}, tsb={}",
                    context.atletaId(), tsbStr);

            return buildResult(
                    SkillSeverity.WARNING,
                    SkillConfidence.HIGH,
                    false,
                    "TSB insuficiente para intervalado: tsbProntidaoAtual=" + tsbStr
                            + " (mínimo: " + TSB_MINIMO_ELEGIBILIDADE + ")",
                    condicoes, evidence, recommendations);
        }
        evidence.add("tsbProntidaoAtual=" + String.format("%.1f", input.tsbProntidaoAtual())
                + " >= limiar=" + TSB_MINIMO_ELEGIBILIDADE);
        condicoes.add("TSB de prontidão adequado — portão 2 passou");

        // ── PORTÃO 3: Fase de periodização ───────────────────────────────────

        String fase = input.fasePeriodizacao() != null
                ? input.fasePeriodizacao().trim().toUpperCase()
                : "";

        if (FASES_BLOQUEADORAS.contains(fase)) {
            condicoes.add("Fase " + fase + " não permite intervalados — portão 3 bloqueado");
            evidence.add("fasePeriodizacao=" + fase + " pertence ao conjunto de fases bloqueadoras");
            recommendations.add("Nesta fase " + fase + " focar em treinos regenerativos e volume leve.");

            log.warn("IntervaladoElegibilidadeSkill WARNING (fase): atletaId={}, fase={}",
                    context.atletaId(), fase);

            return buildResult(
                    SkillSeverity.WARNING,
                    SkillConfidence.HIGH,
                    false,
                    "Fase " + fase.toLowerCase() + " não permite treinos intervalados. "
                            + "Recovery e Taper requerem baixa intensidade.",
                    condicoes, evidence, recommendations);
        }
        condicoes.add("Fase " + fase + " compatível com intervalados — portão 3 passou");
        evidence.add("fasePeriodizacao=" + fase + " — fase elegível");

        // ── PORTÃO 4: Recuperação mínima desde último intervalado ─────────────

        if (input.diasDesdeUltimoIntervalado() < DIAS_RECUPERACAO_MINIMO) {
            String diasStr = String.valueOf(input.diasDesdeUltimoIntervalado());
            condicoes.add("Apenas " + diasStr + " dia(s) desde último intervalado — recuperação insuficiente (mínimo: "
                    + DIAS_RECUPERACAO_MINIMO + " dias)");
            evidence.add("diasDesdeUltimoIntervalado=" + diasStr + " < mínimo=" + DIAS_RECUPERACAO_MINIMO);
            recommendations.add("Aguardar pelo menos " + DIAS_RECUPERACAO_MINIMO
                    + " dias desde o último treino intensivo antes do próximo intervalado.");

            log.warn("IntervaladoElegibilidadeSkill WARNING (recuperação): atletaId={}, dias={}",
                    context.atletaId(), diasStr);

            return buildResult(
                    SkillSeverity.WARNING,
                    SkillConfidence.HIGH,
                    false,
                    "Recuperação insuficiente: apenas " + diasStr + " dia(s) desde último intervalado "
                            + "(mínimo: " + DIAS_RECUPERACAO_MINIMO + " dias)",
                    condicoes, evidence, recommendations);
        }
        condicoes.add("Recuperação adequada (" + input.diasDesdeUltimoIntervalado()
                + " dias desde último intensivo) — portão 4 passou");
        evidence.add("diasDesdeUltimoIntervalado=" + input.diasDesdeUltimoIntervalado()
                + " >= mínimo=" + DIAS_RECUPERACAO_MINIMO);

        // ── PORTÃO 5: Ramp rate ───────────────────────────────────────────────

        if (input.rampRateAtual() > RAMP_RATE_LIMITE) {
            String rampStr = String.format("%.1f", input.rampRateAtual());
            condicoes.add("Ramp rate=" + rampStr + " acima do limite seguro de " + RAMP_RATE_LIMITE
                    + " — portão 5 bloqueado");
            evidence.add("rampRateAtual=" + rampStr + " > limite=" + RAMP_RATE_LIMITE);
            recommendations.add("Reduzir o incremento de carga semanal para abaixo de " + RAMP_RATE_LIMITE
                    + " antes de introduzir treinos intensos.");

            log.warn("IntervaladoElegibilidadeSkill WARNING (ramp rate): atletaId={}, rampRate={}",
                    context.atletaId(), rampStr);

            return buildResult(
                    SkillSeverity.WARNING,
                    SkillConfidence.MEDIUM,
                    false,
                    "Ramp rate excessivo: " + rampStr + " (limite seguro: " + RAMP_RATE_LIMITE + ")",
                    condicoes, evidence, recommendations);
        }
        condicoes.add("Ramp rate=" + String.format("%.1f", input.rampRateAtual())
                + " dentro do limite seguro — portão 5 passou");
        evidence.add("rampRateAtual=" + String.format("%.1f", input.rampRateAtual())
                + " <= limite=" + RAMP_RATE_LIMITE);

        // ── Todos os portões passaram — atleta ELEGÍVEL ───────────────────────

        recommendations.add("Atleta apto para treino intervalado. Selecionar categoria e volume adequados à fase "
                + fase + " e ao CTL=" + String.format("%.1f", input.ctlAtual()) + ".");
        recommendations.add("Monitorar TSB pós-treino e RPE para ajuste da próxima sessão intensa.");

        log.info("IntervaladoElegibilidadeSkill ELEGÍVEL: atletaId={}, tsb={}, ctl={}, fase={}, rampRate={}",
                context.atletaId(),
                String.format("%.1f", input.tsbProntidaoAtual()),
                String.format("%.1f", input.ctlAtual()),
                fase,
                String.format("%.1f", input.rampRateAtual()));

        return buildResult(
                SkillSeverity.INFO,
                SkillConfidence.HIGH,
                true,
                "Todos os portões de elegibilidade passaram. Fase: " + fase
                        + ". Atleta apto para intervalado.",
                condicoes, evidence, recommendations);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Factory interna
    // ─────────────────────────────────────────────────────────────────────────

    private SkillResult<IntervaladoElegibilidadePayload> buildResult(
            SkillSeverity severity,
            SkillConfidence confidence,
            boolean elegivel,
            String motivo,
            List<String> condicoes,
            List<String> evidence,
            List<String> recommendations) {

        IntervaladoElegibilidadePayload payload =
                new IntervaladoElegibilidadePayload(elegivel, motivo, List.copyOf(condicoes));

        return new SkillResult<>(
                SKILL_KEY,
                SKILL_VERSION,
                severity,
                confidence,
                payload,
                List.copyOf(evidence),
                List.copyOf(recommendations));
    }
}
