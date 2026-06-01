package br.com.menthoros.backend.skills.race;

import br.com.menthoros.backend.routing.ModelRouter;
import br.com.menthoros.backend.routing.TaskComplexity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RaceProjectionNarrativeGeneratorTest {

    @Mock private ModelRouter modelRouter;
    @Mock private ChatClient chatClient;
    @Mock private ChatClient.ChatClientRequestSpec requestSpec;
    @Mock private ChatClient.CallResponseSpec callResponseSpec;

    private RaceProjectionNarrativeGenerator generator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        when(modelRouter.route(TaskComplexity.STANDARD)).thenReturn(chatClient);
        stubChatClient(chatClient, validLlmResponse());

        generator = new RaceProjectionNarrativeGenerator(modelRouter, new DefaultResourceLoader(), objectMapper);
        generator.initSkillContent(); // simula @PostConstruct
    }

    // sc_001: resposta bem formada → campos corretamente mapeados
    @Test
    void generate_wellFormedResponse_mapsFieldsCorrectly() {
        Map<String, Object> ctx = Map.of("weeks_to_race", 6);

        RaceProjectionNarrativeGenerator.NarrativeOutput output = generator.generate(ctx);

        assertThat(output.progressionNarrative()).isNotBlank();
        assertThat(output.keyAssumptions()).isNotEmpty();
        assertThat(output.coachNote()).isNotBlank();
    }

    // sc_002: key_assumptions limitados a MAX 5 itens
    @Test
    void generate_llmReturnsMoreThan5Assumptions_truncatedTo5() {
        String manyAssumptions = """
                {
                  "progression_narrative": "Progressão estável.",
                  "key_assumptions": ["a1","a2","a3","a4","a5","a6","a7"],
                  "coach_note": "Mantenha o ritmo."
                }
                """;
        stubChatClient(chatClient, manyAssumptions);

        RaceProjectionNarrativeGenerator.NarrativeOutput output = generator.generate(Map.of("x", 1));

        assertThat(output.keyAssumptions()).hasSize(5);
    }

    // sc_003: narrativa truncada a 500 chars
    @Test
    void generate_narrativeLongerThan500Chars_truncated() {
        String longNarrative = "A".repeat(600);
        String json = String.format("""
                {
                  "progression_narrative": "%s",
                  "key_assumptions": ["ok"],
                  "coach_note": "Notas."
                }
                """, longNarrative);
        stubChatClient(chatClient, json);

        RaceProjectionNarrativeGenerator.NarrativeOutput output = generator.generate(Map.of("x", 1));

        assertThat(output.progressionNarrative()).hasSize(500);
    }

    // sc_004: quando confiança=LOW, resposta contém linguagem de incerteza
    @Test
    void generate_lowConfidenceContext_narrativeContainsUncertaintyLanguage() {
        String lowConfJson = """
                {
                  "progression_narrative": "Com base nos dados disponíveis, esta é uma estimativa preliminar.",
                  "key_assumptions": ["Dados insuficientes: menos de 6 sessões qualificadas"],
                  "coach_note": "Priorize registrar mais treinos com monitor cardíaco."
                }
                """;
        stubChatClient(chatClient, lowConfJson);

        RaceProjectionNarrativeGenerator.NarrativeOutput output = generator.generate(Map.of("confidence", "LOW"));

        assertThat(output.keyAssumptions().get(0)).containsIgnoringCase("dados");
    }

    // sc_005: resposta envolvedora de markdown (```json) é limpada
    @Test
    void generate_markdownWrappedJson_parsedCorrectly() {
        String markdown = "```json\n" + validLlmResponse() + "\n```";
        stubChatClient(chatClient, markdown);

        RaceProjectionNarrativeGenerator.NarrativeOutput output = generator.generate(Map.of("x", 1));

        assertThat(output.progressionNarrative()).isNotBlank();
    }

    // sc_006: erro no Haiku → fallback para Sonnet
    @Test
    void generate_haikuFails_fallsBackToSonnet() {
        when(chatClient.prompt()).thenThrow(new RuntimeException("Haiku timeout"));
        ChatClient sonnet = org.mockito.Mockito.mock(ChatClient.class);
        when(modelRouter.route(TaskComplexity.COMPLEX)).thenReturn(sonnet);
        stubChatClient(sonnet, validLlmResponse());

        RaceProjectionNarrativeGenerator.NarrativeOutput output = generator.generate(Map.of("x", 1));

        assertThat(output.progressionNarrative()).isNotBlank();
    }

    // sc_007: context null → IllegalArgumentException
    @Test
    void generate_nullContext_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> generator.generate(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // sc_008: falha total (haiku + sonnet) → fallback de texto estático retornado
    @Test
    void generate_bothModelsFail_returnsFallbackNarrative() {
        when(chatClient.prompt()).thenThrow(new RuntimeException("Haiku down"));
        when(modelRouter.route(TaskComplexity.COMPLEX)).thenThrow(new RuntimeException("Sonnet down"));

        RaceProjectionNarrativeGenerator.NarrativeOutput output = generator.generate(Map.of("x", 1));

        assertThat(output.progressionNarrative()).isNotBlank();
        assertThat(output.keyAssumptions()).isNotEmpty();
        assertThat(output.coachNote()).isNotBlank();
    }

    // --- helpers ---

    private String validLlmResponse() {
        return """
                {
                  "progression_narrative": "João mantém progressão consistente há 10 semanas.",
                  "key_assumptions": ["Treinos em condições normais", "Sem lesões"],
                  "coach_note": "Manter apenas treinos leves nos próximos 7 dias."
                }
                """;
    }

    private void stubChatClient(ChatClient client, String response) {
        var promptSpec = org.mockito.Mockito.mock(ChatClient.ChatClientRequestSpec.class);
        var optionsSpec = org.mockito.Mockito.mock(ChatClient.ChatClientRequestSpec.class);
        var systemSpec = org.mockito.Mockito.mock(ChatClient.ChatClientRequestSpec.class);
        var userSpec = org.mockito.Mockito.mock(ChatClient.ChatClientRequestSpec.class);
        var callSpec = org.mockito.Mockito.mock(ChatClient.CallResponseSpec.class);

        when(client.prompt()).thenReturn(promptSpec);
        when(promptSpec.options(any())).thenReturn(optionsSpec);
        when(optionsSpec.system(any(String.class))).thenReturn(systemSpec);
        when(systemSpec.user(any(String.class))).thenReturn(userSpec);
        when(userSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn(response);
    }
}
