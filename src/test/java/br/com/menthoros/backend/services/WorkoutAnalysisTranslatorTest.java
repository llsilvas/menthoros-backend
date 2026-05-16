package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.llm.AnaliseWorkoutRawDto;
import br.com.menthoros.backend.enums.PrimaryAnalysisCause;
import br.com.menthoros.backend.routing.ModelRouter;
import br.com.menthoros.backend.routing.TaskComplexity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkoutAnalysisTranslatorTest {

    @Mock
    private ModelRouter modelRouter;

    @Mock
    private ChatClient haikuClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callSpec;

    private WorkoutAnalysisTranslator translator;

    @BeforeEach
    void setUp() {
        translator = new WorkoutAnalysisTranslator(modelRouter);
        when(modelRouter.route(TaskComplexity.STANDARD)).thenReturn(haikuClient);
        when(haikuClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
    }

    @Test
    void translates_text_fields_to_portuguese() {
        when(callSpec.content())
                .thenReturn("Execução mais difícil que esperado")
                .thenReturn("Fadiga acumulada detectada")
                .thenReturn("Recuperação ativa nos próximos 2 dias")
                .thenReturn("Delta RPE +3 com TSB negativo indica fadiga");

        AnaliseWorkoutRawDto raw = new AnaliseWorkoutRawDto(
                "Execution harder than expected",
                "Accumulated fatigue detected",
                PrimaryAnalysisCause.ACCUMULATED_FATIGUE,
                "Active recovery next 2 days",
                List.of("FATIGUE_DETECTED", "RECOVERY_NEEDED"),
                6,
                "RPE delta +3 with negative TSB indicates fatigue"
        );

        AnaliseWorkoutRawDto result = translator.translate(raw);

        assertThat(result.summary()).isEqualTo("Execução mais difícil que esperado");
        assertThat(result.technicalInterpretation()).isEqualTo("Fadiga acumulada detectada");
        assertThat(result.recommendation()).isEqualTo("Recuperação ativa nos próximos 2 dias");
        assertThat(result.rationale()).isEqualTo("Delta RPE +3 com TSB negativo indica fadiga");
    }

    @Test
    void does_not_translate_enums_and_score() {
        when(callSpec.content()).thenReturn("translated");

        AnaliseWorkoutRawDto raw = new AnaliseWorkoutRawDto(
                "Some summary", "Some interpretation",
                PrimaryAnalysisCause.NORMAL,
                "Some recommendation",
                List.of("GOOD_EXECUTION"),
                9,
                "Some rationale"
        );

        AnaliseWorkoutRawDto result = translator.translate(raw);

        assertThat(result.primaryCause()).isEqualTo(PrimaryAnalysisCause.NORMAL);
        assertThat(result.tags()).containsExactly("GOOD_EXECUTION");
        assertThat(result.executionScore()).isEqualTo(9);
    }

    @Test
    void null_input_throws_illegal_argument() {
        assertThatThrownBy(() -> translator.translate(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void null_text_field_is_returned_as_null() {
        AnaliseWorkoutRawDto raw = new AnaliseWorkoutRawDto(
                null, null, PrimaryAnalysisCause.NORMAL, null, List.of(), 8, null
        );

        AnaliseWorkoutRawDto result = translator.translate(raw);

        assertThat(result.summary()).isNull();
        verify(haikuClient, never()).prompt();
    }
}
