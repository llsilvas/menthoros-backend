package br.com.menthoros.backend.services.prompt;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.services.helper.TreinoHistoricoProvider;
import br.com.menthoros.backend.services.helper.TreinoHistoricoProvider.ContextoTreino;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * O bloco "Últimos 5 treinos" do prompt é o que o modelo lê para decidir a próxima semana. Se o
 * tipo mostrado for o inferido pelo sync (heurística de duração/FC), o modelo conclui que o atleta
 * não vem fazendo longos e não sobe volume — o mesmo bug que travava a progressão do lado do
 * cálculo, aqui pelo lado do texto.
 *
 * <p>Complementa {@link PlanoTreinoPromptBuilderGoldenTest}: o golden congela o prompt inteiro dos
 * arquétipos e falharia por qualquer motivo; este teste aponta a asserção nesta regra.</p>
 */
@DisplayName("PlanoTreinoPromptBuilder — tipo do treino no histórico do prompt")
class PlanoTreinoPromptBuilderTipoEfetivoTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 1, 15);
    private static final LocalDate INICIO_SEMANA = LocalDate.of(2026, 1, 19);

    @Test
    @DisplayName("longão vinculado ao planejado aparece como LONGO, não como o TEMPO_RUN inferido")
    void historicoMostraTipoPrescrito() {
        String prompt = montarPrompt(vinculado(
                treino(HOJE.minusDays(3), TipoTreino.TEMPO_RUN), TipoTreino.LONGO));

        assertThat(prompt).contains("- 2026-01-12: LONGO - ");
        assertThat(prompt).doesNotContain("- 2026-01-12: TEMPO_RUN - ");
    }

    @Test
    @DisplayName("treino sem vínculo continua exibindo o tipo executado")
    void historicoSemVinculoMostraTipoExecutado() {
        String prompt = montarPrompt(treino(HOJE.minusDays(3), TipoTreino.TEMPO_RUN));

        assertThat(prompt).contains("- 2026-01-12: TEMPO_RUN - ");
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private String montarPrompt(TreinoRealizado... treinos) {
        TreinoHistoricoProvider provider = mock(TreinoHistoricoProvider.class);
        when(provider.prepararContexto(any()))
                .thenReturn(new ContextoTreino(HOJE, List.of(treinos), List.of(), List.of()));

        Atleta atleta = Atleta.builder()
                .nome("Teste TipoEfetivo")
                .nivelExperiencia(NivelExperiencia.INTERMEDIARIO)
                .objetivo("Melhorar condicionamento")
                .diasDisponiveis(diasUteis())
                .diaPreferidoLongo(DiaSemana.SABADO)
                .temLesao(false)
                .build();
        PlanoMetaDados meta = PlanoMetaDados.builder()
                .atleta(atleta)
                .diaPreferidoLongo(DiaSemana.SABADO)
                .build();

        PlanoTreinoPromptBuilder builder = PlanoPromptArquetipos.builder(provider);

        try (MockedStatic<LocalDate> now = mockStatic(LocalDate.class, CALLS_REAL_METHODS)) {
            now.when(LocalDate::now).thenReturn(HOJE);
            return builder.buildOptimizedPrompt(atleta, meta, null, INICIO_SEMANA, diasUteis()).prompt();
        }
    }

    private TreinoRealizado treino(LocalDate data, TipoTreino tipo) {
        TreinoRealizado tr = new TreinoRealizado();
        tr.setDataTreino(data);
        tr.setTipoTreino(tipo);
        tr.setDistanciaKm(BigDecimal.valueOf(18.0));
        tr.setDuracaoMin(Duration.ofMinutes(88));
        tr.setTssCalculado(110);
        tr.setPercepcaoEsforco(6);
        tr.setPaceMedia(Duration.ofSeconds(340));
        return tr;
    }

    private TreinoRealizado vinculado(TreinoRealizado realizado, TipoTreino tipoPrescrito) {
        TreinoPlanejado planejado = new TreinoPlanejado();
        planejado.setTipoTreino(tipoPrescrito);
        realizado.setTreinoPlanejado(planejado);
        return realizado;
    }

    private List<DiaSemana> diasUteis() {
        return List.of(DiaSemana.SEGUNDA, DiaSemana.QUARTA, DiaSemana.SEXTA, DiaSemana.SABADO);
    }
}
