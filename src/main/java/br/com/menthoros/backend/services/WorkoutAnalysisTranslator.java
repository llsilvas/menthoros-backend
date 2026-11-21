package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.llm.AnaliseWorkoutRawDto;
import br.com.menthoros.backend.routing.ModelRouter;
import br.com.menthoros.backend.routing.TaskComplexity;
import br.com.menthoros.backend.services.prompt.PromptTemplateLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * Traduz os campos textuais de uma análise de treino de inglês para português.
 * Campos enum (primaryCause, tags) e numéricos (executionScore) NÃO são traduzidos.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkoutAnalysisTranslator {

    private static final String TRANSLATE_PROMPT_TEMPLATE = "translate-field-prompt.txt";

    private final ModelRouter modelRouter;
    private final PromptTemplateLoader templateLoader;

    /**
     * Traduz os campos textuais livres do AnaliseWorkoutRawDto para português.
     *
     * Idempotent: YES — mesma entrada produz mesma saída (temperatura baixa da rota simple).
     * Side Effects: External API call (rota SIMPLE, OpenAI)
     * Tenant-aware: NO
     *
     * @param raw análise em inglês retornada pelo LLM de análise
     * @return novo record com campos summary, technicalInterpretation, recommendation e rationale traduzidos
     */
    public AnaliseWorkoutRawDto translate(AnaliseWorkoutRawDto raw) {
        if (raw == null) {
            throw new IllegalArgumentException("AnaliseWorkoutRawDto não pode ser null");
        }
        log.debug("Traduzindo análise: summary={}", raw.summary());

        ChatClient client = modelRouter.route(TaskComplexity.SIMPLE);

        String summaryPt = translateField(client, raw.summary());
        String interpretationPt = translateField(client, raw.technicalInterpretation());
        String recommendationPt = translateField(client, raw.recommendation());
        String rationalePt = translateField(client, raw.rationale());

        log.debug("Tradução concluída: summary_pt={}", summaryPt);

        // primaryCause, tags e executionScore não são traduzidos
        return new AnaliseWorkoutRawDto(
                summaryPt,
                interpretationPt,
                raw.primaryCause(),
                recommendationPt,
                raw.tags(),
                raw.executionScore(),
                rationalePt
        );
    }

    private String translateField(ChatClient client, String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        return client.prompt()
                .user(templateLoader.loadTemplate(TRANSLATE_PROMPT_TEMPLATE).formatted(text))
                .call()
                .content();
    }
}
