package br.com.menthoros.backend.services.prompt;

import br.com.menthoros.backend.dto.DecisaoProgressao;
import br.com.menthoros.backend.enums.EstadoProgressao;
import br.com.menthoros.backend.services.helper.TreinoHistoricoProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@DisplayName("Integração: DecisaoProgressao → prompt gerado")
class ProgressaoPromptIntegracaoTest {

    @Test
    @DisplayName("atleta consistente — bloco PROGREDIR aparece no prompt")
    void atletaConsistenteIncluiProgredir() {
        PlanoPromptArquetipos.Arquetipo arq = PlanoPromptArquetipos.todos().get(0); // inicianteSemLesao
        DecisaoProgressao decisao = new DecisaoProgressao(
                EstadoProgressao.PROGREDIR, 0.06, 10, true, "aderência e recuperação adequadas");

        String prompt = buildPrompt(arq, decisao);

        assertThat(prompt).contains("PROGREDIR");
    }

    @Test
    @DisplayName("atleta com TSB negativo — bloco REDUZIR aparece no prompt")
    void atletaComTsbNegativoIncluiReduzir() {
        PlanoPromptArquetipos.Arquetipo arq = PlanoPromptArquetipos.todos().get(1); // avancadoTsbBaixo
        DecisaoProgressao decisao = new DecisaoProgressao(
                EstadoProgressao.REDUZIR, -0.05, -10, false, "TSB crítico (-20.0)");

        String prompt = buildPrompt(arq, decisao);

        assertThat(prompt).contains("REDUZIR");
    }

    private String buildPrompt(PlanoPromptArquetipos.Arquetipo arq, DecisaoProgressao decisao) {
        TreinoHistoricoProvider provider = mock(TreinoHistoricoProvider.class);
        when(provider.prepararContexto(any())).thenReturn(arq.contexto());

        PlanoTreinoPromptBuilder builder = PlanoPromptArquetipos.builder(provider);

        try (MockedStatic<LocalDate> now = mockStatic(LocalDate.class, CALLS_REAL_METHODS)) {
            now.when(LocalDate::now).thenReturn(PlanoPromptArquetipos.HOJE);
            return builder.buildOptimizedPrompt(
                    arq.atleta(), arq.meta(), arq.prova(),
                    arq.inicioSemana(), arq.diasEfetivos(), decisao).prompt();
        }
    }
}
