package br.com.menthoros.backend.services.prompt;

import br.com.menthoros.backend.services.helper.TreinoHistoricoProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;

import java.time.LocalDate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@DisplayName("EvalCandidateFixtures")
class EvalCandidateFixturesTest {

    static Stream<EvalCandidateFixtures.Candidato> candidatos() {
        return EvalCandidateFixtures.todos().stream();
    }

    @Test
    @DisplayName("produz 1 candidato por arquétipo existente, cada um com skeleton não-nulo")
    void produzUmCandidatoPorArquetipo() {
        var candidatos = EvalCandidateFixtures.todos();

        assertThat(candidatos).hasSameSizeAs(PlanoPromptArquetipos.todos());
        assertThat(candidatos).allSatisfy(c -> {
            assertThat(c.skeleton()).isNotNull();
            assertThat(c.skeleton().sessions()).isNotNull();
        });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("candidatos")
    @DisplayName("expor os parâmetros extras não altera o wiring do prompt-builder (sem skeleton == overload de 5 args)")
    void naoAlteraWiringDoBuilder(EvalCandidateFixtures.Candidato candidato) {
        var arq = candidato.arquetipo();
        TreinoHistoricoProvider provider = mock(TreinoHistoricoProvider.class);
        when(provider.prepararContexto(any())).thenReturn(arq.contexto());
        PlanoTreinoPromptBuilder builder = PlanoPromptArquetipos.builder(provider);

        try (MockedStatic<LocalDate> now = mockStatic(LocalDate.class, CALLS_REAL_METHODS)) {
            now.when(LocalDate::now).thenReturn(PlanoPromptArquetipos.HOJE);

            var viaOverloadCurto = builder.buildOptimizedPrompt(
                    arq.atleta(), arq.meta(), arq.prova(), arq.inicioSemana(), arq.diasEfetivos());
            var viaOverloadCompleto = builder.buildOptimizedPrompt(
                    arq.atleta(), arq.meta(), arq.prova(), arq.inicioSemana(), arq.diasEfetivos(),
                    null, null, null, false);

            assertThat(viaOverloadCompleto.system()).isEqualTo(viaOverloadCurto.system());
            assertThat(viaOverloadCompleto.user()).isEqualTo(viaOverloadCurto.user());
        }
    }
}
