package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.domain.planner.WeekPlanSkeleton;
import org.jspecify.annotations.Nullable;

/**
 * Desfecho da Fase 2 (pré-prompt) do cálculo do {@link WeekPlanSkeleton}
 * (planner-engine-enforcement, item 8.5.h): threadado até a persistência (Fase 3) em vez de
 * recomputado lá.
 *
 * <p>Antes deste tipo, o skeleton pré-prompt (usado para guiar o LLM) e o skeleton do estágio 2
 * (usado para <b>enforcar</b> o plano final) eram calculados em dois pontos diferentes — mesma
 * entrada, mas a Fase 2 roda <b>fora</b> de transação (design.md D1: nenhuma conexão do pool
 * enquanto o LLM pensa) e a Fase 3 roda <b>dentro</b> de uma. Um caminho lazy do Hibernate acessado
 * pelo {@code PlannerEngine} podia falhar só na Fase 2 (sem sessão ativa) e suceder na Fase 3 —
 * fail-open silenciosamente mascarava isso: a geração caía no pipeline legado (o LLM nunca viu o
 * skeleton), mas o estágio 2 recomputava um skeleton "do nada" e enforçava o plano contra ele,
 * produzindo {@code FAILED} espúrio sobre um plano que nunca teve chance de seguir a prescrição.
 *
 * @param skeleton  o skeleton computado, ou {@code null} quando o planner está desligado ou a
 *                  computação falhou (fail-open)
 * @param fallback  {@code true} somente quando {@code planner-engine.enabled=true} e a computação
 *                  falhou (fail-open) — o pipeline legado gerou o plano sem o skeleton. {@code false}
 *                  quando o flag está desligado (skeleton nulo por config, não por falha) ou quando a
 *                  computação teve sucesso.
 */
public record SkeletonPrePrompt(@Nullable WeekPlanSkeleton skeleton, boolean fallback) {

    public SkeletonPrePrompt {
        if (fallback && skeleton != null) {
            throw new IllegalArgumentException(
                    "SkeletonPrePrompt inconsistente: fallback=true com skeleton presente — use as factories estáticas (desligado/sucesso/viaFallback)");
        }
    }

    public static SkeletonPrePrompt desligado() {
        return new SkeletonPrePrompt(null, false);
    }

    public static SkeletonPrePrompt sucesso(WeekPlanSkeleton skeleton) {
        return new SkeletonPrePrompt(skeleton, false);
    }

    public static SkeletonPrePrompt viaFallback() {
        return new SkeletonPrePrompt(null, true);
    }
}
