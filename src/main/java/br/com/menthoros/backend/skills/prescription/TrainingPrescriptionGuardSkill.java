package br.com.menthoros.backend.skills.prescription;

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
 * Skill de guarda de segurança para a prescrição semanal de treinos.
 *
 * <p>Valida o plano proposto pelo LLM contra os limites fisiológicos do atleta,
 * detectando violações de volume, TSS, fase de periodização e restrições de lesão.
 * Um resultado {@link SkillSeverity#BLOCKER} impede a prescrição de ser enviada ao atleta.</p>
 *
 * <h2>Regras de validação</h2>
 * <ol>
 *   <li><b>Volume excessivo:</b> volume proposto &gt; média últimas 4 semanas × 1.10 → BLOCKER</li>
 *   <li><b>TSS excessivo:</b> TSS total proposto &gt; metaTssSemanal × 1.15 → BLOCKER</li>
 *   <li><b>Lesão ativa + dias consecutivos:</b> mais de 2 dias consecutivos de alta intensidade
 *       quando {@code temLesaoAtiva} → BLOCKER</li>
 *   <li><b>Fase RECOVERY/TAPER + sessão-chave:</b> TAPER ou RECOVERY com sessão INTERVALADO
 *       ou LONGO → BLOCKER</li>
 *   <li><b>Ramp rate global elevado:</b> {@code rampRateAtual > 8.0} → WARNING (não BLOCKER)</li>
 * </ol>
 *
 * <p><b>Idempotente:</b> SIM — análise determinística sobre os dados de entrada.</p>
 * <p><b>Side Effects:</b> NONE — não escreve em banco nem chama APIs externas.</p>
 * <p><b>Tenant-aware:</b> SIM — context.tenantId() disponível para rastreabilidade.</p>
 */
@Slf4j
@Component
public class TrainingPrescriptionGuardSkill
        implements DomainSkill<TrainingPrescriptionGuardInput, TrainingPrescriptionGuardPayload> {

    private static final String SKILL_KEY     = "training-prescription-guard-v1";
    private static final String SKILL_VERSION = "1.0";

    /** Limite de incremento de volume semanal (10% sobre a média de 4 semanas). */
    private static final double VOLUME_RAMP_RATE_MAX = 1.10;

    /** Limite de TSS semanal (15% acima da meta). */
    private static final double TSS_MARGIN_MAX = 1.15;

    /** Threshold de ramp rate semanal que dispara WARNING (não BLOCKER). */
    private static final double RAMP_RATE_WARNING = 8.0;

    /** Tipos de treino de alta intensidade (key-sessions). */
    private static final Set<String> ALTA_INTENSIDADE = Set.of("INTERVALADO", "LONGO", "TEMPO_RUN", "TIRO", "SUBIDA", "PROVA");

    /** Sessões-chave bloqueadas em fases de TAPER/RECOVERY. */
    private static final Set<String> SESSOES_BLOQUEADAS_TAPER = Set.of("INTERVALADO", "LONGO");

    /** Fases que bloqueiam sessões-chave. */
    private static final Set<String> FASES_RESTRICTIVAS = Set.of("TAPER", "RECOVERY", "POS_PROVA", "SEMANA_PROVA");

    /** Ordem canônica dos dias da semana para detecção de consecutividade. */
    private static final List<String> ORDEM_SEMANA = List.of(
            "DOMINGO", "SEGUNDA", "TERCA", "QUARTA", "QUINTA", "SEXTA", "SABADO"
    );

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
        return SkillCategory.PRESCRIPTION_GUARD;
    }

    /**
     * Executa a validação de segurança sobre o plano semanal proposto.
     *
     * <p><b>Idempotente:</b> SIM — mesmos inputs produzem sempre o mesmo resultado.</p>
     * <p><b>Side Effects:</b> NONE</p>
     * <p><b>Tenant-aware:</b> SIM — context.tenantId() disponível.</p>
     *
     * @param input   plano proposto e métricas de carga do atleta
     * @param context contexto de execução com atletaId, tenantId e dataReferencia
     * @return resultado com payload de aprovação, totais calculados e lista de violações
     * @throws IllegalArgumentException se {@code input} for null
     */
    @Override
    public SkillResult<TrainingPrescriptionGuardPayload> execute(
            TrainingPrescriptionGuardInput input,
            SkillContext context) {

        if (input == null) {
            throw new IllegalArgumentException("TrainingPrescriptionGuardInput cannot be null");
        }

        log.debug("TrainingPrescriptionGuardSkill iniciada: atletaId={}, tenantId={}, fase={}, sessoes={}",
                context.atletaId(), context.tenantId(),
                input.fasePeriodizacao(),
                input.treinosPropostos() != null ? input.treinosPropostos().size() : 0);

        List<TreinoPlanejadoResumo> sessoes = input.treinosPropostos() != null
                ? input.treinosPropostos()
                : List.of();

        // ── Calcular totais ───────────────────────────────────────────────────
        double tssTotal = sessoes.stream().mapToDouble(TreinoPlanejadoResumo::tssEstimado).sum();
        double volumeTotalKm = sessoes.stream().mapToDouble(TreinoPlanejadoResumo::distanciaKm).sum();

        List<String> violacoes = new ArrayList<>();
        List<String> evidence  = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();
        boolean temWarning = false;

        evidence.add(String.format("Sessões propostas: %d", sessoes.size()));
        evidence.add(String.format("Volume total proposto: %.1f km", volumeTotalKm));
        evidence.add(String.format("TSS total proposto: %.1f", tssTotal));
        evidence.add(String.format("Média volume 4 semanas: %.1f km", input.mediaVolumeSemanas4()));
        evidence.add(String.format("Meta TSS semanal: %.1f", input.metaTssSemanal()));
        evidence.add(String.format("Ramp rate atual: %.1f pts/sem", input.rampRateAtual()));
        evidence.add(String.format("Fase periodização: %s", input.fasePeriodizacao()));
        evidence.add(String.format("Lesão ativa: %s", input.temLesaoAtiva()));

        // ── REGRA 1: Volume excessivo (> 10% sobre média 4 semanas) ──────────
        double limiteVolume = input.mediaVolumeSemanas4() * VOLUME_RAMP_RATE_MAX;
        if (volumeTotalKm > limiteVolume) {
            String msg = String.format(
                    "volume excessivo: %.1f km proposto supera %.1f km (média %.1f km × 1.10)",
                    volumeTotalKm, limiteVolume, input.mediaVolumeSemanas4());
            violacoes.add(msg);
            evidence.add("BLOCKER: " + msg);
            recommendations.add(String.format(
                    "Reduzir volume total para no máximo %.1f km (ramp rate ≤ 10%%).", limiteVolume));

            log.warn("TrainingPrescriptionGuardSkill BLOCKER (volume): atletaId={}, volume={}, limite={}",
                    context.atletaId(), volumeTotalKm, limiteVolume);
        }

        // ── REGRA 2: TSS excessivo (> 15% acima da meta) ─────────────────────
        double limiteTss = input.metaTssSemanal() * TSS_MARGIN_MAX;
        if (tssTotal > limiteTss) {
            String msg = String.format(
                    "tss excessivo: %.1f proposto supera %.1f (meta %.1f × 1.15)",
                    tssTotal, limiteTss, input.metaTssSemanal());
            violacoes.add(msg);
            evidence.add("BLOCKER: " + msg);
            recommendations.add(String.format(
                    "Reduzir TSS total para no máximo %.1f (15%% acima da meta).", limiteTss));

            log.warn("TrainingPrescriptionGuardSkill BLOCKER (TSS): atletaId={}, tss={}, limite={}",
                    context.atletaId(), tssTotal, limiteTss);
        }

        // ── REGRA 3: Lesão ativa + dias consecutivos de alta intensidade ──────
        if (input.temLesaoAtiva()) {
            int maxConsecutivosAltaIntensidade = contarMaxDiasConsecutivosAltaIntensidade(sessoes);
            if (maxConsecutivosAltaIntensidade > 2) {
                String msg = String.format(
                        "lesão ativa com %d dias consecutivos de alta intensidade (limite: 2)",
                        maxConsecutivosAltaIntensidade);
                violacoes.add(msg);
                evidence.add("BLOCKER: " + msg);
                recommendations.add(
                        "Com lesão ativa, limitar sessões de alta intensidade a no máximo 2 dias consecutivos. " +
                        "Inserir dias de recuperação (REGENERATIVO ou FACIL) entre sessões intensas.");

                log.warn("TrainingPrescriptionGuardSkill BLOCKER (lesão+consecutivos): atletaId={}, consecutivos={}",
                        context.atletaId(), maxConsecutivosAltaIntensidade);
            }
        }

        // ── REGRA 4: Fase RECOVERY/TAPER com sessão-chave ────────────────────
        String fase = input.fasePeriodizacao() != null
                ? input.fasePeriodizacao().trim().toUpperCase()
                : "";

        if (FASES_RESTRICTIVAS.contains(fase)) {
            for (TreinoPlanejadoResumo sessao : sessoes) {
                String tipo = sessao.tipoTreino() != null ? sessao.tipoTreino().trim().toUpperCase() : "";
                if (SESSOES_BLOQUEADAS_TAPER.contains(tipo)) {
                    String msg = String.format(
                            "fase %s não permite sessão-chave %s (recovery/taper exigem baixa intensidade)",
                            fase.toLowerCase(), tipo);
                    violacoes.add(msg);
                    evidence.add("BLOCKER: " + msg);
                    recommendations.add(String.format(
                            "Na fase %s substituir %s por sessões REGENERATIVO ou FACIL.",
                            fase, tipo));

                    log.warn("TrainingPrescriptionGuardSkill BLOCKER (fase+sessão): atletaId={}, fase={}, tipo={}",
                            context.atletaId(), fase, tipo);
                    break; // Uma violação por regra é suficiente para registrar
                }
            }
        }

        // ── REGRA 5: Ramp rate elevado → WARNING (não BLOCKER) ───────────────
        if (input.rampRateAtual() > RAMP_RATE_WARNING) {
            temWarning = true;
            evidence.add(String.format("WARNING: ramp rate=%.1f supera limite seguro de %.1f",
                    input.rampRateAtual(), RAMP_RATE_WARNING));
            recommendations.add(String.format(
                    "Ramp rate elevado (%.1f pts/sem > %.1f). Monitorar sinais de overtraining nas próximas semanas.",
                    input.rampRateAtual(), RAMP_RATE_WARNING));

            log.info("TrainingPrescriptionGuardSkill WARNING (ramp rate): atletaId={}, rampRate={}",
                    context.atletaId(), input.rampRateAtual());
        }

        // ── Determinar resultado final ────────────────────────────────────────
        boolean aprovado = violacoes.isEmpty();
        SkillSeverity severity;
        SkillConfidence confidence;

        if (!aprovado) {
            severity   = SkillSeverity.BLOCKER;
            confidence = SkillConfidence.HIGH;
        } else if (temWarning) {
            severity   = SkillSeverity.WARNING;
            confidence = SkillConfidence.MEDIUM;
        } else {
            severity   = SkillSeverity.INFO;
            confidence = sessoes.isEmpty() ? SkillConfidence.LOW : SkillConfidence.HIGH;
        }

        TrainingPrescriptionGuardPayload payload = new TrainingPrescriptionGuardPayload(
                aprovado, tssTotal, volumeTotalKm, List.copyOf(violacoes));

        log.debug("TrainingPrescriptionGuardSkill concluída: atletaId={}, aprovado={}, violacoes={}, severity={}",
                context.atletaId(), aprovado, violacoes.size(), severity);

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
    // Lógica auxiliar
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Conta o número máximo de dias consecutivos com sessões de alta intensidade
     * no plano proposto, considerando a ordem canônica dos dias da semana.
     */
    private int contarMaxDiasConsecutivosAltaIntensidade(List<TreinoPlanejadoResumo> sessoes) {
        // Mapear dias com alta intensidade para índices na semana
        List<Integer> indicesAltaIntensidade = sessoes.stream()
                .filter(s -> s.tipoTreino() != null
                        && ALTA_INTENSIDADE.contains(s.tipoTreino().trim().toUpperCase()))
                .map(s -> {
                    String dia = s.diaSemana() != null ? s.diaSemana().trim().toUpperCase() : "";
                    return ORDEM_SEMANA.indexOf(dia);
                })
                .filter(idx -> idx >= 0)
                .sorted()
                .distinct()
                .toList();

        if (indicesAltaIntensidade.isEmpty()) {
            return 0;
        }

        int maxConsec = 1;
        int atual = 1;

        for (int i = 1; i < indicesAltaIntensidade.size(); i++) {
            if (indicesAltaIntensidade.get(i) == indicesAltaIntensidade.get(i - 1) + 1) {
                atual++;
                maxConsec = Math.max(maxConsec, atual);
            } else {
                atual = 1;
            }
        }

        return maxConsec;
    }
}
