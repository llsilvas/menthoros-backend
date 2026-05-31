package br.com.menthoros.backend.skills.race;

import br.com.menthoros.backend.routing.ModelRouter;
import br.com.menthoros.backend.routing.TaskComplexity;
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
 *
 * Idempotent: NO — chamada externa ao LLM; resultados podem variar.
 * Side Effects: External API call (Anthropic)
 * Tenant-aware: NO — não há dados de tenant no contexto enviado ao LLM.
 */
@Slf4j
@Component
public class RaceProjectionNarrativeGenerator {

    private static final String SKILL_PATH = "classpath:skills/race/projection/SKILL.md";
    private static final int MAX_KEY_ASSUMPTIONS = 5;
    private static final int MAX_NARRATIVE_CHARS = 500;
    private static final int MAX_COACH_NOTE_CHARS = 400;

    private final ModelRouter modelRouter;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;

    private String cachedSkillContent;

    public RaceProjectionNarrativeGenerator(ModelRouter modelRouter,
                                             ResourceLoader resourceLoader,
                                             ObjectMapper objectMapper) {
        this.modelRouter = modelRouter;
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void initSkillContent() {
        this.cachedSkillContent = loadSkill();
    }

    /**
     * Gera os três campos textuais do output: progressionNarrative, keyAssumptions, coachNote.
     *
     * @param input  contexto completo da projeção serializado como mapa
     * @return NarrativeOutput com os três campos gerados pelo LLM
     */
    public NarrativeOutput generate(Map<String, Object> context) {
        if (context == null) {
            throw new IllegalArgumentException("LLM context cannot be null");
        }

        String userPrompt = buildUserPrompt(context);

        try {
            String rawJson = callHaikuWithFallback(userPrompt);
            return parseAndValidate(rawJson);
        } catch (Exception e) {
            log.error("Falha na geração de narrativa LLM: {}", e.getMessage(), e);
            return fallbackNarrative();
        }
    }

    private String callHaikuWithFallback(String userPrompt) {
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
                    .content();
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
                    .content();
        }
    }

    @SuppressWarnings("unchecked")
    private NarrativeOutput parseAndValidate(String rawJson) throws IOException {
        String cleaned = stripMarkdownCodeBlock(rawJson);
        Map<String, Object> parsed = objectMapper.readValue(cleaned, Map.class);

        String narrative = truncate((String) parsed.get("progression_narrative"), MAX_NARRATIVE_CHARS);
        String coachNote = truncate((String) parsed.get("coach_note"), MAX_COACH_NOTE_CHARS);
        List<String> rawAssumptions = (List<String>) parsed.getOrDefault("key_assumptions", List.of());
        List<String> assumptions = rawAssumptions.stream()
                .limit(MAX_KEY_ASSUMPTIONS)
                .toList();

        return new NarrativeOutput(narrative, assumptions, coachNote);
    }

    private String buildUserPrompt(Map<String, Object> context) {
        try {
            return "Generate the race projection narrative for this context. Respond with valid JSON only:\n"
                    + objectMapper.writeValueAsString(context);
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

    private String stripMarkdownCodeBlock(String raw) {
        if (raw == null) return "{}";
        String trimmed = raw.strip();
        if (trimmed.startsWith("```")) {
            int first = trimmed.indexOf('\n');
            int last = trimmed.lastIndexOf("```");
            if (first >= 0 && last > first) {
                return trimmed.substring(first + 1, last).strip();
            }
        }
        return trimmed;
    }

    private String truncate(String value, int maxChars) {
        if (value == null) return "";
        return value.length() > maxChars ? value.substring(0, maxChars) : value;
    }

    private NarrativeOutput fallbackNarrative() {
        return new NarrativeOutput(
                "Projeção calculada com base no histórico de treinos disponível.",
                List.of("Narrativa automática indisponível temporariamente."),
                "Consulte seu coach para orientações personalizadas."
        );
    }

    /**
     * Saída estruturada do LLM para os três campos textuais do RaceProjectionOutput.
     */
    public record NarrativeOutput(
            String progressionNarrative,
            List<String> keyAssumptions,
            String coachNote
    ) {}
}
