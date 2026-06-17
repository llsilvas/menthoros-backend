package br.com.menthoros.backend.skills.race;

import br.com.menthoros.backend.routing.ModelRouter;
import br.com.menthoros.backend.routing.TaskComplexity;
import br.com.menthoros.backend.services.prompt.PromptTemplateLoader;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Gera narrativa de progressão, premissas e coach_note via LLM (Claude Haiku 4.5).
 *
 * Usa o system prompt definido em SKILL.md (cacheado em @PostConstruct).
 * Fallback automático para Claude Sonnet 4.6 em caso de erro/timeout do Haiku.
 * O parse da resposta usa output estruturado ({@code .entity()}); os limites de tamanho
 * (premissas/narrativa/coach_note) são instruídos no próprio prompt, não no código.
 *
 * Idempotent: NO — chamada externa ao LLM; resultados podem variar.
 * Side Effects: External API call (Anthropic)
 * Tenant-aware: NO — não há dados de tenant no contexto enviado ao LLM.
 */
@Slf4j
@Component
public class RaceProjectionNarrativeGenerator {

    private static final String SKILL_PATH = "classpath:skills/race/projection/SKILL.md";
    private static final String USER_PROMPT_TEMPLATE = "race-projection-user-prompt.txt";

    private final ModelRouter modelRouter;
    private final ResourceLoader resourceLoader;
    private final PromptTemplateLoader templateLoader;
    // ObjectMapper mantido apenas para serializar o contexto no prompt (buildUserPrompt);
    // o parse da resposta do LLM agora é feito por .entity().
    private final ObjectMapper objectMapper;

    private String cachedSkillContent;

    public RaceProjectionNarrativeGenerator(ModelRouter modelRouter,
                                             ResourceLoader resourceLoader,
                                             PromptTemplateLoader templateLoader,
                                             ObjectMapper objectMapper) {
        this.modelRouter = modelRouter;
        this.resourceLoader = resourceLoader;
        this.templateLoader = templateLoader;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void initSkillContent() {
        this.cachedSkillContent = loadSkill();
    }

    /**
     * Gera os três campos textuais do output: progressionNarrative, keyAssumptions, coachNote.
     *
     * @param context  contexto completo da projeção serializado como mapa
     * @return NarrativeOutput com os três campos gerados pelo LLM
     */
    public NarrativeOutput generate(Map<String, Object> context) {
        if (context == null) {
            throw new IllegalArgumentException("LLM context cannot be null");
        }

        String userPrompt = buildUserPrompt(context);

        try {
            NarrativeOutputDto dto = callWithFallback(userPrompt);
            List<String> assumptions = dto.keyAssumptions() != null ? dto.keyAssumptions() : List.of();
            return new NarrativeOutput(dto.progressionNarrative(), assumptions, dto.coachNote());
        } catch (Exception e) {
            log.error("Falha na geração de narrativa LLM: {}", e.getMessage(), e);
            return fallbackNarrative();
        }
    }

    private NarrativeOutputDto callWithFallback(String userPrompt) {
        ChatClient haiku = modelRouter.route(TaskComplexity.STANDARD);
        try {
            return haiku.prompt()
                    .options(AnthropicChatOptions.builder()
                            .temperature(0.2)
                            .maxTokens(1000)
                            .build())
                    .system(cachedSkillContent)
                    .user(userPrompt)
                    .call()
                    .entity(NarrativeOutputDto.class);
        } catch (Exception haikuEx) {
            log.warn("Haiku falhou, usando Sonnet como fallback: {}", haikuEx.getMessage());
            ChatClient sonnet = modelRouter.route(TaskComplexity.COMPLEX);
            return sonnet.prompt()
                    .options(AnthropicChatOptions.builder()
                            .temperature(0.2)
                            .maxTokens(1000)
                            .build())
                    .system(cachedSkillContent)
                    .user(userPrompt)
                    .call()
                    .entity(NarrativeOutputDto.class);
        }
    }

    private String buildUserPrompt(Map<String, Object> context) {
        try {
            return templateLoader.loadAndFormat(USER_PROMPT_TEMPLATE, objectMapper.writeValueAsString(context));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize LLM context", e);
        }
    }

    private String loadSkill() {
        try {
            var resource = resourceLoader.getResource(SKILL_PATH);
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load skill: " + SKILL_PATH, e);
        }
    }

    private NarrativeOutput fallbackNarrative() {
        return new NarrativeOutput(
                "Projeção calculada com base no histórico de treinos disponível.",
                List.of("Narrativa automática indisponível temporariamente."),
                "Consulte seu coach para orientações personalizadas."
        );
    }

    /**
     * Saída bruta do LLM (JSON em snake_case). Package-private para permitir que o teste
     * construa instâncias ao stubar {@code .entity(NarrativeOutputDto.class)}.
     */
    record NarrativeOutputDto(
            @JsonProperty("progression_narrative")
            String progressionNarrative,

            @JsonProperty("key_assumptions")
            List<String> keyAssumptions,

            @JsonProperty("coach_note")
            String coachNote
    ) {}

    /**
     * Saída estruturada do LLM para os três campos textuais do RaceProjectionOutput.
     */
    public record NarrativeOutput(
            String progressionNarrative,
            List<String> keyAssumptions,
            String coachNote
    ) {}
}
