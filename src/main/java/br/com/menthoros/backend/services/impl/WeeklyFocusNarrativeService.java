package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.entity.RevisaoSemanal;
import br.com.menthoros.backend.enums.FocusSource;
import br.com.menthoros.backend.repository.RevisaoSemanalRepository;
import br.com.menthoros.backend.routing.ModelRouter;
import br.com.menthoros.backend.routing.TaskComplexity;
import br.com.menthoros.backend.services.prompt.PromptTemplateLoader;
import br.com.menthoros.backend.services.quality.WeeklyFocusConsistencyChecker;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Redige o {@code nextWeekFocus} por LLM sobre uma revisão JÁ persistida (D8).
 *
 * <p>Roda em {@code @Async} com executor dedicado — o {@code RevisaoSemanalListener} executa na
 * thread que comitou o encerramento, que é tanto o clique do coach quanto o
 * {@code EncerramentoSemanaScheduler} fechando N planos em lote; uma chamada LLM síncrona ali
 * serializaria o job inteiro.
 *
 * <p>A revisão nunca fica sem foco: ela nasce com o template determinístico (Fatia 1 + D5) e este
 * serviço só sobrescreve quando a narrativa passa pelo {@link WeeklyFocusConsistencyChecker}.
 * Falha do modelo, narrativa inconsistente ou flag desligada ⇒ o template permanece.
 *
 * <p><b>Limite conhecido:</b> não há timeout de resposta na chamada — os clientes LLM do módulo não
 * têm nenhum configurado (gap registrado no {@code CLAUDE.md}), e configurá-lo aqui mudaria o
 * comportamento de todas as rotas (geração de plano, análise de treino). Fica para a change
 * {@code add-external-call-resilience}. O pool dedicado limita o dano: uma chamada pendurada
 * consome uma thread deste executor, nunca a do coach.
 */
@Service
@Slf4j
public class WeeklyFocusNarrativeService {

    private static final String PROMPT_TEMPLATE = "weekly-focus-user-prompt.txt";
    private static final String METRICA_REPROVADA = "weekly_review.focus.rejected";

    private final RevisaoSemanalRepository revisaoSemanalRepository;
    private final ModelRouter modelRouter;
    private final PromptTemplateLoader templateLoader;
    private final WeeklyFocusConsistencyChecker checker;
    private final MeterRegistry meterRegistry;
    private final boolean llmHabilitado;

    public WeeklyFocusNarrativeService(RevisaoSemanalRepository revisaoSemanalRepository,
                                       ModelRouter modelRouter,
                                       PromptTemplateLoader templateLoader,
                                       WeeklyFocusConsistencyChecker checker,
                                       MeterRegistry meterRegistry,
                                       @Value("${menthoros.weekly-review.llm.enabled:false}") boolean llmHabilitado) {
        this.revisaoSemanalRepository = revisaoSemanalRepository;
        this.modelRouter = modelRouter;
        this.templateLoader = templateLoader;
        this.checker = checker;
        this.meterRegistry = meterRegistry;
        this.llmHabilitado = llmHabilitado;
    }

    /**
     * Substitui o foco template pela narrativa por IA, quando ela é consistente com o
     * {@code recommendationType} congelado.
     *
     * <p><b>Idempotent:</b> YES — reexecutar reescreve o mesmo campo; o sinal determinístico da
     * Fatia 1 nunca é tocado.
     * <p><b>Side Effects:</b> Database update ({@code next_week_focus}, {@code focus_source}),
     * External API call (LLM), métrica Micrometer.
     * <p><b>Tenant-aware:</b> YES — o {@code tenantId} vem do evento de encerramento e é usado no log.
     */
    @Async("weeklyFocusExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Retryable(retryFor = Exception.class, maxAttempts = 2, backoff = @Backoff(delay = 2000))
    public void gerarNarrativa(UUID revisaoId, UUID tenantId) {
        if (!llmHabilitado) {
            return; // kill-switch: fica no template, sem custo de LLM
        }
        RevisaoSemanal revisao = revisaoSemanalRepository.findById(revisaoId).orElse(null);
        if (revisao == null) {
            log.warn("[weekly-focus] revisão {} não encontrada (tenant {})", revisaoId, tenantId);
            return;
        }

        String narrativa;
        try {
            narrativa = chamarModelo(revisao);
        } catch (Exception e) {
            // Engolido de propósito: a revisão já tem o template coerente, e propagar aqui só
            // encheria o log do executor. Mesma filosofia do try/catch do RevisaoSemanalListener.
            log.warn("[weekly-focus] falha ao gerar narrativa da revisão {} — mantém template", revisaoId, e);
            return;
        }

        if (!checker.isConsistent(narrativa, revisao.getRecommendationType())) {
            meterRegistry.counter(METRICA_REPROVADA).increment();
            log.warn("[weekly-focus] narrativa reprovada pelo checker (tipo {}) — mantém template",
                    revisao.getRecommendationType());
            return;
        }

        revisao.setNextWeekFocus(narrativa.trim());
        revisao.setFocusSource(FocusSource.LLM);
        revisaoSemanalRepository.save(revisao);
        log.info("[weekly-focus] narrativa gravada na revisão {} (tenant {})", revisaoId, tenantId);
    }

    private String chamarModelo(RevisaoSemanal revisao) {
        String prompt = templateLoader.loadAndFormat(PROMPT_TEMPLATE, dadosDoPrompt(revisao));
        return modelRouter.route(TaskComplexity.SIMPLE)
                .prompt()
                .user(prompt)
                .call()
                .content();
    }

    /** Só o sinal já consolidado — o LLM redige, não recalcula (non-goal explícito da change). */
    private String dadosDoPrompt(RevisaoSemanal revisao) {
        return """
                {"recommendationType":"%s","adherenceStatus":"%s","completionRate":%s,\
                "sufficientData":%s,"tsbFim":%s}"""
                .formatted(
                        revisao.getRecommendationType(),
                        revisao.getAdherenceStatus(),
                        revisao.getCompletionRate(),
                        revisao.isSufficientData(),
                        revisao.getPlanoSemanal().getTsbFim());
    }
}
