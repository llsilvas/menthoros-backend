package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.entity.RevisaoSemanal;
import br.com.menthoros.backend.enums.FocusSource;
import br.com.menthoros.backend.enums.NivelAderencia;
import br.com.menthoros.backend.enums.RecommendationType;
import br.com.menthoros.backend.repository.RevisaoSemanalRepository;
import br.com.menthoros.backend.routing.ModelRouter;
import br.com.menthoros.backend.routing.TaskComplexity;
import br.com.menthoros.backend.services.helper.RevisaoSemanalCalculator;
import br.com.menthoros.backend.services.prompt.PromptTemplateLoader;
import br.com.menthoros.backend.services.quality.WeeklyFocusConsistencyChecker;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.client.ChatClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Narrativa do foco por LLM (D8/D10): flag como kill-switch, checker como guarda de consistência e
 * falha do modelo que nunca deixa a revisão sem foco.
 */
@ExtendWith(MockitoExtension.class)
class WeeklyFocusNarrativeServiceTest {

    @Mock private RevisaoSemanalRepository revisaoSemanalRepository;
    @Mock private ModelRouter modelRouter;
    @Mock private PromptTemplateLoader templateLoader;

    private final WeeklyFocusConsistencyChecker checker = new WeeklyFocusConsistencyChecker();
    private MeterRegistry meterRegistry;

    private UUID revisaoId;
    private RevisaoSemanal revisao;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        revisaoId = UUID.randomUUID();
        revisao = revisaoComTipo(RecommendationType.MAINTAIN);
    }

    private WeeklyFocusNarrativeService service(boolean flagLigada) {
        return new WeeklyFocusNarrativeService(
                revisaoSemanalRepository, modelRouter, templateLoader, checker, meterRegistry, flagLigada);
    }

    @Nested
    @DisplayName("gerarNarrativa — kill-switch")
    class KillSwitch {

        @Test
        @DisplayName("flag desligada não chama o LLM e preserva o template (CA-LLM)")
        void flagDesligadaNaoChamaLlm() {
            service(false).gerarNarrativa(revisaoId, UUID.randomUUID());

            verifyNoInteractions(modelRouter, templateLoader, revisaoSemanalRepository);
            assertThat(revisao.getFocusSource()).isEqualTo(FocusSource.TEMPLATE);
        }
    }

    @Nested
    @DisplayName("gerarNarrativa — flag ligada")
    @MockitoSettings(strictness = Strictness.LENIENT)
    class FlagLigada {

        @Test
        @DisplayName("narrativa consistente é persistida com focusSource=LLM")
        void narrativaConsistentePersiste() {
            stubLlm("Mantenha a carga desta semana e priorize a consistência dos treinos.");

            service(true).gerarNarrativa(revisaoId, UUID.randomUUID());

            ArgumentCaptor<RevisaoSemanal> captor = ArgumentCaptor.forClass(RevisaoSemanal.class);
            verify(revisaoSemanalRepository).save(captor.capture());
            assertThat(captor.getValue().getFocusSource()).isEqualTo(FocusSource.LLM);
            assertThat(captor.getValue().getNextWeekFocus())
                    .isEqualTo("Mantenha a carga desta semana e priorize a consistência dos treinos.");
        }

        @Test
        @DisplayName("narrativa que contraria o tipo é descartada — template preservado (CA-LLM)")
        void narrativaInconsistenteCaiNoTemplate() {
            stubLlm("Aumente o volume em 15% e progrida para treinos mais longos.");

            service(true).gerarNarrativa(revisaoId, UUID.randomUUID());

            verify(revisaoSemanalRepository, never()).save(any());
            assertThat(meterRegistry.counter("weekly_review.focus.rejected").count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("falha do LLM preserva o template e não propaga exceção (D8)")
        void falhaDoLlmPreservaTemplate() {
            when(revisaoSemanalRepository.findById(revisaoId)).thenReturn(Optional.of(revisao));
            when(templateLoader.loadAndFormat(anyString(), anyString())).thenReturn("prompt");
            when(modelRouter.route(any(TaskComplexity.class)))
                    .thenThrow(new IllegalStateException("modelo indisponível"));

            service(true).gerarNarrativa(revisaoId, UUID.randomUUID());

            verify(revisaoSemanalRepository, never()).save(any());
            assertThat(revisao.getNextWeekFocus())
                    .isEqualTo(RevisaoSemanalCalculator.nextWeekFocusTemplate(RecommendationType.MAINTAIN));
            assertThat(revisao.getFocusSource()).isEqualTo(FocusSource.TEMPLATE);
        }

        @Test
        @DisplayName("revisão inexistente é no-op silencioso")
        void revisaoInexistente() {
            when(revisaoSemanalRepository.findById(revisaoId)).thenReturn(Optional.empty());

            service(true).gerarNarrativa(revisaoId, UUID.randomUUID());

            verify(revisaoSemanalRepository, never()).save(any());
            verifyNoInteractions(modelRouter);
        }

        private void stubLlm(String resposta) {
            when(revisaoSemanalRepository.findById(revisaoId)).thenReturn(Optional.of(revisao));
            when(templateLoader.loadAndFormat(anyString(), anyString())).thenReturn("prompt");

            ChatClient chatClient = mock(ChatClient.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
            when(modelRouter.route(any(TaskComplexity.class))).thenReturn(chatClient);
            when(chatClient.prompt().user(anyString()).call().content()).thenReturn(resposta);
        }
    }

    private RevisaoSemanal revisaoComTipo(RecommendationType tipo) {
        PlanoSemanal plano = new PlanoSemanal();
        plano.setId(UUID.randomUUID());
        plano.setTsbFim(new BigDecimal("-5"));
        return RevisaoSemanal.builder()
                .id(revisaoId)
                .planoSemanal(plano)
                .recommendationType(tipo)
                .adherenceStatus(NivelAderencia.MEDIA)
                .completionRate(new BigDecimal("70.00"))
                .sufficientData(true)
                .nextWeekFocus(RevisaoSemanalCalculator.nextWeekFocusTemplate(tipo))
                .focusSource(FocusSource.TEMPLATE)
                .geradaEm(Instant.now())
                .build();
    }
}
