package br.com.menthoros.backend.skills.recovery;

import br.com.menthoros.backend.enums.MetricasThresholds;
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
 * Skill de domínio que avalia o estado de recuperação e carga do atleta.
 *
 * <p>Classifica o atleta em quatro estados fisiológicos com base no TSB pré-treino
 * (Training Stress Balance) e em indicadores de sobrecarga acumulada:</p>
 * <ul>
 *   <li><b>FRESCO</b>: TSB >= 5 e rampRate <= 5 — condições ideais para intensidade.</li>
 *   <li><b>NEUTRO</b>: TSB >= -10 e < 5 — treinamento normal, sem restrições.</li>
 *   <li><b>FATIGADO</b>: TSB < -10 e >= -25 — alerta de fadiga, monitorar.</li>
 *   <li><b>SOBRECARREGADO</b>: TSB < -25 OU dias consecutivos >= 7 — descanso obrigatório.</li>
 * </ul>
 *
 * <p>Lesão ativa sempre adiciona recomendação de redução de intensidade,
 * independentemente da classificação por TSB.</p>
 *
 * <p><b>Idempotente:</b> SIM — análise puramente determinística, sem estado mutável.</p>
 * <p><b>Side Effects:</b> NONE — não escreve em banco nem chama APIs externas.</p>
 * <p><b>Tenant-aware:</b> SIM — contexto de tenantId disponível via {@code SkillContext}.</p>
 */
@Slf4j
@Component
public class RecoveryCargaSkill implements DomainSkill<RecoveryCargaInput, RecoveryCargaPayload> {

    private static final String SKILL_KEY = "recovery-carga-v1";
    private static final String SKILL_VERSION = "1.0";

    /*
     * Os limiares vêm de MetricasThresholds — a skill não tem números próprios.
     *
     * Antes eram quatro literais aqui: dois repetiam valores que o enum já tinha (5.0 e -10.0) e
     * dois só existiam nesta classe (-25 e 7 dias), com o -25 copiado à mão também no
     * RevisaoSemanalCalculator. Três fontes para o mesmo conceito: mudar uma não mexia nas outras, e
     * nenhum teste acusava. A skill roda hoje em shadow mode dentro do MetricasAlertaService (o
     * resultado só vai para log.debug), então a divergência não chegava ao treinador — mas chegaria
     * no dia em que ela passasse a valer.
     */
    private static final double TSB_FRESCO_MIN = MetricasThresholds.TSB_FORMA_IDEAL_MIN;
    private static final double TSB_NEUTRO_MIN = MetricasThresholds.TSB_ACUMULANDO_FADIGA;
    private static final double TSB_SOBRECARREGADO_MAX = MetricasThresholds.TSB_PISO_RECOVERY;

    // Threshold de ramp rate para qualificar estado FRESCO (condição AND com TSB)
    private static final double RAMP_RATE_FRESCO_MAX = 5.0;

    // Limiar de dias consecutivos para disparar SOBRECARREGADO
    private static final int DIAS_CONSECUTIVOS_SOBRECARGA = MetricasThresholds.DIAS_CONSECUTIVOS_RECOVERY;

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
        return SkillCategory.RECOVERY;
    }

    /**
     * Executa a análise de recuperação e carga com base nas métricas do atleta.
     *
     * <p><b>Idempotente:</b> SIM — mesmos inputs produzem sempre o mesmo resultado.</p>
     * <p><b>Side Effects:</b> NONE</p>
     * <p><b>Tenant-aware:</b> SIM — context.tenantId() disponível para rastreabilidade.</p>
     *
     * @param input   métricas atuais de carga do atleta (CTL, ATL, TSB, rampRate, etc.)
     * @param context contexto de execução com atletaId, tenantId e dataReferencia
     * @return resultado classificado com payload, evidências e recomendações
     * @throws IllegalArgumentException se input ou context forem nulos
     */
    @Override
    public SkillResult<RecoveryCargaPayload> execute(RecoveryCargaInput input, SkillContext context) {
        if (input == null) {
            throw new IllegalArgumentException("RecoveryCargaInput cannot be null");
        }
        if (context == null) {
            throw new IllegalArgumentException("SkillContext cannot be null");
        }

        log.debug("Executando RecoveryCargaSkill: atletaId={}, tenantId={}, tsb={}, rampRate={}, diasConsec={}",
                context.atletaId(), context.tenantId(),
                input.tsbProntidaoAtual(), input.rampRateAtual(), input.diasConsecutivosTreino());

        Classificacao classificacao = classificar(input);

        List<String> evidence = montarEvidencias(input, classificacao);
        List<String> recommendations = montarRecomendacoes(input, classificacao);

        RecoveryCargaPayload payload = new RecoveryCargaPayload(
                classificacao.nome,
                classificacao.descricao,
                classificacao.requerDescanso,
                classificacao.bloqueiaAltaIntensidade
        );

        SkillResult<RecoveryCargaPayload> result = new SkillResult<>(
                SKILL_KEY,
                SKILL_VERSION,
                classificacao.severity,
                classificacao.confidence,
                payload,
                evidence,
                recommendations
        );

        log.debug("RecoveryCargaSkill concluída: atletaId={}, classificacao={}, severity={}",
                context.atletaId(), classificacao.nome, classificacao.severity);

        return result;
    }

    // =====================================================================
    // Lógica de classificação
    // =====================================================================

    private Classificacao classificar(RecoveryCargaInput input) {
        double tsb = input.tsbProntidaoAtual();
        double rampRate = input.rampRateAtual();
        int diasConsec = input.diasConsecutivosTreino() != null ? input.diasConsecutivosTreino() : 0;

        // Prioridade 1: SOBRECARREGADO — TSB < -25 OU >= 7 dias consecutivos
        if (tsb < TSB_SOBRECARREGADO_MAX || diasConsec >= DIAS_CONSECUTIVOS_SOBRECARGA) {
            return Classificacao.SOBRECARREGADO;
        }

        // Prioridade 2: FATIGADO — -25 <= TSB < -10
        if (tsb < TSB_NEUTRO_MIN) {
            return Classificacao.FATIGADO;
        }

        // Prioridade 3: FRESCO — TSB >= 5 E rampRate <= 5
        if (tsb >= TSB_FRESCO_MIN && rampRate <= RAMP_RATE_FRESCO_MAX) {
            return Classificacao.FRESCO;
        }

        // Default: NEUTRO — TSB >= -10 mas não satisfaz condição FRESCO
        return Classificacao.NEUTRO;
    }

    // =====================================================================
    // Evidências e recomendações
    // =====================================================================

    private List<String> montarEvidencias(RecoveryCargaInput input, Classificacao classificacao) {
        List<String> evidencias = new ArrayList<>();

        evidencias.add(String.format("TSB pré-treino: %.1f", input.tsbProntidaoAtual()));
        evidencias.add(String.format("CTL atual: %.1f", input.ctlAtual()));
        evidencias.add(String.format("ATL atual: %.1f", input.atlAtual()));
        evidencias.add(String.format("Ramp Rate: %.1f pts/sem", input.rampRateAtual()));

        if (input.diasConsecutivosTreino() != null) {
            evidencias.add(String.format("Dias consecutivos treinando: %d", input.diasConsecutivosTreino()));
        }

        if (input.temLesaoAtiva()) {
            evidencias.add("Lesão ativa registrada — restrição de intensidade em vigor.");
        }

        evidencias.add("Classificação resultante: " + classificacao.nome);

        return List.copyOf(evidencias);
    }

    private List<String> montarRecomendacoes(RecoveryCargaInput input, Classificacao classificacao) {
        List<String> recomendacoes = new ArrayList<>();

        switch (classificacao) {
            case FRESCO -> recomendacoes.add(
                    "Atleta em condições ideais de prontidão. Janela adequada para treinos de alta intensidade ou provas.");

            case NEUTRO -> recomendacoes.add(
                    "Estado equilibrado — prosseguir com o plano normal, sem aumentar carga abruptamente.");

            case FATIGADO -> {
                recomendacoes.add("Fadiga moderada detectada. Considerar reduzir intensidade dos próximos treinos.");
                recomendacoes.add("Monitorar sinais de overtraining: queda de desempenho, irritabilidade, distúrbios do sono.");
            }

            case SOBRECARREGADO -> {
                recomendacoes.add("Sobrecarga crítica. Descanso ativo ou passivo OBRIGATÓRIO antes do próximo treino intenso.");
                recomendacoes.add("Reduzir volume em 30-40% e priorizar recuperação nas próximas 48-72h.");
                if (input.diasConsecutivosTreino() != null && input.diasConsecutivosTreino() >= DIAS_CONSECUTIVOS_SOBRECARGA) {
                    recomendacoes.add(String.format(
                            "%d dias consecutivos sem descanso — risco elevado de lesão por overuse.",
                            input.diasConsecutivosTreino()));
                }
            }
        }

        // Lesão ativa sempre adiciona recomendação específica de redução de intensidade
        if (input.temLesaoAtiva()) {
            recomendacoes.add("Lesão ativa: reduzir intensidade e evitar impacto elevado. Consultar fisioterapeuta antes de retomar treinos intensos.");
        }

        return List.copyOf(recomendacoes);
    }

    // =====================================================================
    // Enum interno de classificação fisiológica
    // =====================================================================

    private enum Classificacao {

        FRESCO(
                "FRESCO",
                "TSB positivo e progressão de carga estável — atleta em prontidão ótima para treinos intensos.",
                SkillSeverity.INFO,
                SkillConfidence.HIGH,
                false,
                false
        ),

        NEUTRO(
                "NEUTRO",
                "TSB em faixa equilibrada — atleta em estado de manutenção, sem fadiga significativa.",
                SkillSeverity.INFO,
                SkillConfidence.MEDIUM,
                false,
                false
        ),

        FATIGADO(
                "FATIGADO",
                "TSB negativo moderado — fadiga acumulada detectada. Monitorar sinais de overtraining.",
                SkillSeverity.WARNING,
                SkillConfidence.HIGH,
                false,
                false
        ),

        SOBRECARREGADO(
                "SOBRECARREGADO",
                "Sobrecarga crítica de treino — TSB muito negativo ou excesso de dias consecutivos. Descanso obrigatório.",
                SkillSeverity.CRITICAL,
                SkillConfidence.HIGH,
                true,
                true
        );

        final String nome;
        final String descricao;
        final SkillSeverity severity;
        final SkillConfidence confidence;
        final boolean requerDescanso;
        final boolean bloqueiaAltaIntensidade;

        Classificacao(String nome, String descricao, SkillSeverity severity,
                      SkillConfidence confidence, boolean requerDescanso,
                      boolean bloqueiaAltaIntensidade) {
            this.nome = nome;
            this.descricao = descricao;
            this.severity = severity;
            this.confidence = confidence;
            this.requerDescanso = requerDescanso;
            this.bloqueiaAltaIntensidade = bloqueiaAltaIntensidade;
        }
    }
}
