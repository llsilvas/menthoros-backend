package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.llm.AthleteMessageDto;
import br.com.menthoros.backend.routing.ModelRouter;
import br.com.menthoros.backend.routing.TaskComplexity;
import br.com.menthoros.backend.services.prompt.PromptTemplateLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AthleteMessageClassifierTest {

    @Mock private ModelRouter modelRouter;
    @Mock private ChatClient haikuClient;
    @Mock private ChatClient.ChatClientRequestSpec requestSpec;
    @Mock private ChatClient.CallResponseSpec callSpec;

    private AthleteMessageClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new AthleteMessageClassifier(
                modelRouter, new PromptTemplateLoader(new DefaultResourceLoader()));
        when(modelRouter.route(TaskComplexity.STANDARD)).thenReturn(haikuClient);
        when(haikuClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
    }

    private static AthleteMessageDto blocoSemTokensMasPrescritivo() {
        // O caso que o regex do validador NÃO pega — a razão de o classificador existir.
        return new AthleteMessageDto(
                "Você completou a distância mesmo cansado.",
                "O treino saiu mais devagar que o planejado.",
                "Seu corpo está pedindo uma pausa.",
                "Melhor não fazer o intervalado de sexta e descansar.");
    }

    @Test
    void sim_do_haiku_marca_como_prescricao() {
        when(callSpec.content()).thenReturn("SIM");

        assertThat(classifier.mandaMudarPlano(blocoSemTokensMasPrescritivo())).isTrue();
        verify(modelRouter).route(TaskComplexity.STANDARD);
    }

    @Test
    void nao_do_haiku_libera() {
        when(callSpec.content()).thenReturn("NAO");

        assertThat(classifier.mandaMudarPlano(blocoSemTokensMasPrescritivo())).isFalse();
    }

    @Test
    void resposta_com_ruido_ainda_e_reconhecida() {
        when(callSpec.content()).thenReturn("  sim, o texto sugere pular o treino.");

        assertThat(classifier.mandaMudarPlano(blocoSemTokensMasPrescritivo())).isTrue();
    }

    @Test
    void falha_do_haiku_e_fail_open() {
        when(callSpec.content()).thenThrow(new RuntimeException("model unavailable"));

        assertThat(classifier.mandaMudarPlano(blocoSemTokensMasPrescritivo())).isFalse();
    }

    @Test
    void prompt_leva_os_quatro_textos() {
        when(callSpec.content()).thenReturn("NAO");

        classifier.mandaMudarPlano(blocoSemTokensMasPrescritivo());

        verify(requestSpec).user(argThat((String p) ->
                p.contains("pedindo uma pausa") && p.contains("intervalado de sexta")));
    }
}
