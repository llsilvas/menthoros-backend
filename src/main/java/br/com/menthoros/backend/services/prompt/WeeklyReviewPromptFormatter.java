package br.com.menthoros.backend.services.prompt;

import br.com.menthoros.backend.entity.RevisaoSemanal;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Renderiza a revisão da semana anterior como contexto do prompt de geração de plano (CA4).
 *
 * <p>É <b>contexto, não comando</b>: a revisão informa o que foi observado e qual foco foi proposto
 * ao coach, sem autorizar o LLM a aplicar nada sozinho — a decisão continua com o treinador, que
 * aprova ou rejeita o plano gerado (CA5).
 */
@Component
public class WeeklyReviewPromptFormatter {

    /** Sem revisão consumível (ausente, fora da janela ou injeção desligada) ⇒ bloco vazio. */
    public String formatarRevisao(@Nullable RevisaoSemanal revisao) {
        if (revisao == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("## 📋 REVISÃO DA SEMANA ANTERIOR\n\n");
        sb.append("- **Recomendação consolidada:** ").append(revisao.getRecommendationType()).append('\n');
        sb.append("- **Aderência:** ").append(revisao.getAdherenceStatus());
        if (revisao.getCompletionRate() != null) {
            sb.append(" (").append(revisao.getCompletionRate()).append("%)");
        }
        sb.append('\n');
        if (!revisao.isSufficientData()) {
            sb.append("- **Atenção:** a semana anterior teve dado insuficiente para conclusão forte.\n");
        }
        if (revisao.getNextWeekFocus() != null && !revisao.getNextWeekFocus().isBlank()) {
            sb.append("- **Foco proposto ao treinador:** ").append(revisao.getNextWeekFocus()).append('\n');
        }
        sb.append("\nUse como contexto da prescrição. NÃO contrarie a recomendação consolidada acima.\n");
        return sb.toString();
    }
}
