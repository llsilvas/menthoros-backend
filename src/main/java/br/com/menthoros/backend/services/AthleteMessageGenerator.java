package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.llm.AthleteMessageDto;
import br.com.menthoros.backend.enums.PrimaryAnalysisCause;
import br.com.menthoros.backend.routing.ModelRouter;
import br.com.menthoros.backend.routing.TaskComplexity;
import br.com.menthoros.backend.services.prompt.PromptTemplateLoader;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Segunda chamada da análise pós-treino: o bloco do atleta em PT-BR (design D2 de
 * {@code analise-ia-treino-atleta}). Roda depois da análise do coach, recebe o mesmo payload
 * numérico mais o {@code primary_cause} resultante, e usa a rota {@code SIMPLE} (gpt-4o-mini) —
 * quatro frases curtas não precisam de raciocínio complexo.
 *
 * <p>Falha aqui NUNCA falha a análise do coach: qualquer exceção vira {@link Optional#empty()}
 * e o atleta apenas não vê o card.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AthleteMessageGenerator {

    private static final String SKILL_PATH = "classpath:skills/analise/athlete-workout-motivation/SKILL.md";
    private static final String USER_PROMPT_TEMPLATE = "athlete-message-user-prompt.txt";

    private final ModelRouter modelRouter;
    private final PromptTemplateLoader templateLoader;
    private final ResourceLoader resourceLoader;

    private String cachedSkillContent;

    @PostConstruct
    void initSkillContent() {
        try {
            var resource = resourceLoader.getResource(SKILL_PATH);
            this.cachedSkillContent = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Falha ao carregar SKILL.md do bloco do atleta: " + e.getMessage(), e);
        }
    }

    /**
     * Idempotent: YES — mesma entrada tende à mesma saída; sem estado gravado.
     * Side Effects: External API call (gpt-4o-mini via rota SIMPLE)
     * Tenant-aware: N/A — opera sobre o payload já montado pelo chamador.
     *
     * @param promptData   JSON numérico do treino ({@link WorkoutAnalysisPromptDataBuilder})
     * @param primaryCause causa resultante da análise do coach (chamada 1)
     * @return o bloco do atleta, ou vazio quando a chamada falha ou devolve nada
     */
    public Optional<AthleteMessageDto> gerar(String promptData, PrimaryAnalysisCause primaryCause) {
        try {
            String userPrompt = templateLoader.loadAndFormat(
                    USER_PROMPT_TEMPLATE, promptData, String.valueOf(primaryCause));

            ChatClient simple = modelRouter.route(TaskComplexity.SIMPLE);
            AthleteMessageDto dto = simple.prompt()
                    .system(cachedSkillContent)
                    .user(userPrompt)
                    .call()
                    .entity(AthleteMessageDto.class);

            return Optional.ofNullable(dto);
        } catch (Exception e) {
            log.warn("Falha ao gerar bloco do atleta (análise do coach segue intacta): {}", e.getMessage());
            return Optional.empty();
        }
    }
}
