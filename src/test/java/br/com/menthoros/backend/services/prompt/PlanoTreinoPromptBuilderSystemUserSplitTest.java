package br.com.menthoros.backend.services.prompt;

import br.com.menthoros.backend.services.helper.TreinoHistoricoProvider;
import br.com.menthoros.backend.services.prompt.PlanoPromptArquetipos.Arquetipo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * {@code PlanoTreinoPromptBuilder#buildOptimizedPrompt} retorna {@code system}/{@code user}
 * separados (system-user-prompt-split, CA1).
 */
@DisplayName("PlanoTreinoPromptBuilder — PromptGerado(system, user, regras)")
class PlanoTreinoPromptBuilderSystemUserSplitTest {

    @Test
    @DisplayName("system é o template estático cru; user carrega o perfil e o histórico dinâmico")
    void systemEUserSeparados() throws IOException {
        Arquetipo arq = PlanoPromptArquetipos.todos().get(0);

        TreinoHistoricoProvider provider = mock(TreinoHistoricoProvider.class);
        when(provider.prepararContexto(any())).thenReturn(arq.contexto());
        PlanoTreinoPromptBuilder builder = PlanoPromptArquetipos.builder(provider);

        PlanoTreinoPromptBuilder.PromptGerado gerado;
        try (MockedStatic<LocalDate> now = mockStatic(LocalDate.class, CALLS_REAL_METHODS)) {
            now.when(LocalDate::now).thenReturn(PlanoPromptArquetipos.HOJE);
            gerado = builder.buildOptimizedPrompt(
                    arq.atleta(), arq.meta(), arq.prova(), arq.inicioSemana(), arq.diasEfetivos());
        }

        String systemCru = lerRecurso("prompts/plano-treino-system.txt");

        assertThat(gerado.system())
                .as("system é lido cru via loadTemplate — byte-idêntico ao arquivo, sem interpolação")
                .isEqualTo(systemCru);
        assertThat(gerado.system())
                .as("system nunca deve conter dado do atleta")
                .doesNotContain(arq.atleta().getNome());

        assertThat(gerado.user())
                .contains("### PERFIL DO ATLETA")
                .contains("### HISTÓRICO RECENTE")
                .contains(arq.atleta().getNome());
        assertThat(gerado.user())
                .as("user não deve reconter o bloco de regras estáticas")
                .doesNotContain("### REGRAS DO PLANO");

        assertThat(gerado.regras()).isNotNull();
    }

    private static String lerRecurso(String nome) throws IOException {
        ClassPathResource resource = new ClassPathResource(nome);
        return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }
}
