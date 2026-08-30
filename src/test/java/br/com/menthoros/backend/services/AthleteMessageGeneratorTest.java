package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.llm.AthleteMessageDto;
import br.com.menthoros.backend.enums.PrimaryAnalysisCause;
import br.com.menthoros.backend.routing.ModelRouter;
import br.com.menthoros.backend.routing.TaskComplexity;
import br.com.menthoros.backend.services.prompt.PromptTemplateLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AthleteMessageGeneratorTest {

    @Mock private ModelRouter modelRouter;
    @Mock private ChatClient simpleClient;
    @Mock private ChatClient.ChatClientRequestSpec requestSpec;
    @Mock private ChatClient.CallResponseSpec callSpec;

    private AthleteMessageGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new AthleteMessageGenerator(
                modelRouter,
                new PromptTemplateLoader(new DefaultResourceLoader()),
                new DefaultResourceLoader());
        generator.initSkillContent();

        when(modelRouter.route(TaskComplexity.SIMPLE)).thenReturn(simpleClient);
        when(simpleClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
    }

    private static AthleteMessageDto fixture() {
        return new AthleteMessageDto(
                "Você segurou o ritmo nos dois blocos.",
                "Saiu como planejado: 58 min contra 61 previstos.",
                "Um 7 num treino previsto como 6 — pesou um pouco mais que o esperado.",
                "Capriche no sono hoje e vale comentar com seu coach como você acorda amanhã.");
    }

    @Test
    void gera_bloco_pela_rota_simple() {
        when(callSpec.entity(AthleteMessageDto.class)).thenReturn(fixture());

        Optional<AthleteMessageDto> result = generator.gerar("{\"actual\":{\"rpe\":7}}",
                PrimaryAnalysisCause.ACCUMULATED_FATIGUE);

        assertThat(result).contains(fixture());
        verify(modelRouter).route(TaskComplexity.SIMPLE);
        verify(modelRouter, never()).route(TaskComplexity.COMPLEX);
    }

    @Test
    void falha_da_chamada_vira_empty_sem_propagar() {
        when(callSpec.entity(AthleteMessageDto.class)).thenThrow(new RuntimeException("model unavailable"));

        assertThat(generator.gerar("{}", PrimaryAnalysisCause.NORMAL)).isEmpty();
    }

    @Test
    void resposta_nula_vira_empty() {
        when(callSpec.entity(AthleteMessageDto.class)).thenReturn(null);

        assertThat(generator.gerar("{}", PrimaryAnalysisCause.NORMAL)).isEmpty();
    }

    @Test
    void prompt_leva_dados_e_primary_cause() {
        when(callSpec.entity(AthleteMessageDto.class)).thenReturn(fixture());

        generator.gerar("{\"actual\":{\"rpe\":9}}", PrimaryAnalysisCause.CNS_FATIGUE);

        verify(requestSpec).user(argThat((String prompt) ->
                prompt.contains("{\"actual\":{\"rpe\":9}}") && prompt.contains("CNS_FATIGUE")));
        verify(requestSpec).system(argThat((String system) ->
                system.contains("athlete-workout-motivation")));
    }
}
