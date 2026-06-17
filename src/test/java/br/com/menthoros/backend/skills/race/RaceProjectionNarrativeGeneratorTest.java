package br.com.menthoros.backend.skills.race;

import br.com.menthoros.backend.routing.ModelRouter;
import br.com.menthoros.backend.routing.TaskComplexity;
import br.com.menthoros.backend.services.prompt.PromptTemplateLoader;
import br.com.menthoros.backend.skills.race.RaceProjectionNarrativeGenerator.NarrativeOutput;
import br.com.menthoros.backend.skills.race.RaceProjectionNarrativeGenerator.NarrativeOutputDto;
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

import java.util.List;
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

    private RaceProjectionNarrativeGenerator generator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        when(modelRouter.route(TaskComplexity.STANDARD)).thenReturn(chatClient);
        stubChatClient(chatClient, validDto());

        DefaultResourceLoader resourceLoader = new DefaultResourceLoader();
        generator = new RaceProjectionNarrativeGenerator(
                modelRouter, resourceLoader, new PromptTemplateLoader(resourceLoader), objectMapper);
        generator.initSkillContent(); // simula @PostConstruct
    }

    // sc_001: DTO bem formado → campos mapeados para NarrativeOutput
    @Test
    void generate_wellFormedResponse_mapsFieldsCorrectly() {
        NarrativeOutput output = generator.generate(Map.of("weeks_to_race", 6));

        assertThat(output.progressionNarrative()).isEqualTo("João mantém progressão consistente há 10 semanas.");
        assertThat(output.keyAssumptions()).containsExactly("Treinos em condições normais", "Sem lesões");
        assertThat(output.coachNote()).isEqualTo("Manter apenas treinos leves nos próximos 7 dias.");
    }

    // key_assumptions nulo no DTO → lista vazia (guarda de null)
    @Test
    void generate_nullAssumptions_mapsToEmptyList() {
        stubChatClient(chatClient, new NarrativeOutputDto("Progressão estável.", null, "Notas."));

        NarrativeOutput output = generator.generate(Map.of("x", 1));

        assertThat(output.keyAssumptions()).isEmpty();
        assertThat(output.progressionNarrative()).isEqualTo("Progressão estável.");
    }

    // sc_006: erro no Haiku → fallback para Sonnet
    @Test
    void generate_haikuFails_fallsBackToSonnet() {
        when(chatClient.prompt()).thenThrow(new RuntimeException("Haiku timeout"));
        ChatClient sonnet = org.mockito.Mockito.mock(ChatClient.class);
        when(modelRouter.route(TaskComplexity.COMPLEX)).thenReturn(sonnet);
        stubChatClient(sonnet, validDto());

        NarrativeOutput output = generator.generate(Map.of("x", 1));

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

        NarrativeOutput output = generator.generate(Map.of("x", 1));

        assertThat(output.progressionNarrative()).isNotBlank();
        assertThat(output.keyAssumptions()).isNotEmpty();
        assertThat(output.coachNote()).isNotBlank();
    }

    // --- helpers ---

    private NarrativeOutputDto validDto() {
        return new NarrativeOutputDto(
                "João mantém progressão consistente há 10 semanas.",
                List.of("Treinos em condições normais", "Sem lesões"),
                "Manter apenas treinos leves nos próximos 7 dias.");
    }

    private void stubChatClient(ChatClient client, NarrativeOutputDto response) {
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
        when(callSpec.entity(NarrativeOutputDto.class)).thenReturn(response);
    }
}
