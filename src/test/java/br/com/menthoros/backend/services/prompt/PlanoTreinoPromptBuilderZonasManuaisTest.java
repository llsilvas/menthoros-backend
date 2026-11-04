package br.com.menthoros.backend.services.prompt;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.FaixaZonaManual;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.enums.ModoZonas;
import br.com.menthoros.backend.services.helper.TreinoHistoricoProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/** coach-zonas-manuais, D2 — o prompt inteiro, não só o bloco de zonas, respeita o modo por eixo (CA1). */
@DisplayName("PlanoTreinoPromptBuilder — zonas manuais")
class PlanoTreinoPromptBuilderZonasManuaisTest {

    private static final List<FaixaZonaManual> FC = List.of(
            new FaixaZonaManual(1, 120, 140), new FaixaZonaManual(2, 140, 150),
            new FaixaZonaManual(3, 153, 160), new FaixaZonaManual(4, 160, 168),
            new FaixaZonaManual(5, 168, 178));

    private static final List<FaixaZonaManual> PACE = List.of(
            new FaixaZonaManual(1, 350, 380), new FaixaZonaManual(2, 320, 350),
            new FaixaZonaManual(3, 300, 315), new FaixaZonaManual(4, 285, 300),
            new FaixaZonaManual(5, 270, 285), new FaixaZonaManual(6, 240, 270));

    @Test
    @DisplayName("CA1: FC manual chega ao prompt com Z2 = 140–150 bpm e a origem declarada")
    void fcManualNoPrompt() {
        Atleta atleta = base().toBuilder().modoZonasFc(ModoZonas.MANUAL).zonasManuaisFc(FC).build();

        String user = montar(atleta).user();

        assertThat(user).contains("| 140-150 bpm").contains("FC: definidas pelo treinador");
    }

    @Test
    @DisplayName("pace manual sem paceLimiar: zonas de pace no prompt e nenhum fallback de 'apenas FC'")
    void paceManualSemLimiar() {
        Atleta atleta = base().toBuilder().paceLimiar(null)
                .modoZonasPace(ModoZonas.MANUAL).zonasManuaisPace(PACE).build();

        String user = montar(atleta).user();

        assertThat(user).contains("- Z2 (Aeróbico): 5.33-5.83 min/km")
                .contains("pace: definidas pelo treinador")
                .doesNotContain("USAR APENAS FC")
                .doesNotContain("Pace Limiar inválido");
    }

    @Test
    @DisplayName("pace manual sem nenhum limiar não cai no ramo de 'fórmula etária'")
    void manualSemLimiaresNaoEhFormulaEtaria() {
        Atleta atleta = base().toBuilder().paceLimiar(null).fcLimiar(null)
                .modoZonasPace(ModoZonas.MANUAL).zonasManuaisPace(PACE).build();

        assertThat(montar(atleta).user()).doesNotContain("estimada por fórmula etária");
    }

    @Test
    @DisplayName("FC manual sem limiar e sem pace: os fallbacks não mandam estimar FC nem usar % da FCmax")
    void fcManualSemLimiaresNaoContradizFaixas() {
        Atleta atleta = base().toBuilder().paceLimiar(null).fcLimiar(null)
                .modoZonasFc(ModoZonas.MANUAL).zonasManuaisFc(FC).build();

        String user = montar(atleta).user();

        assertThat(user).contains("| 140-150 bpm")
                .doesNotContain("FC Limiar não disponível")
                .doesNotContain("FC Limiar e FC Max não disponíveis")
                .doesNotContain("percentual FCmax")
                .contains("Usar apenas FC (zonas definidas pelo treinador)");
    }

    @Test
    @DisplayName("LIMIAR nos dois eixos mantém o cabeçalho de sempre")
    void limiarInalterado() {
        assertThat(montar(base()).user()).contains("**Zonas de Treino Calculadas:**");
    }

    private static Atleta base() {
        return PlanoPromptArquetipos.todos().getFirst().atleta();
    }

    private PlanoTreinoPromptBuilder.PromptGerado montar(Atleta atleta) {
        var arquetipo = PlanoPromptArquetipos.todos().getFirst();
        TreinoHistoricoProvider provider = mock(TreinoHistoricoProvider.class);
        when(provider.prepararContexto(any())).thenReturn(arquetipo.contexto());
        PlanoTreinoPromptBuilder builder = PlanoPromptArquetipos.builder(provider);
        PlanoMetaDados meta = arquetipo.meta();

        try (MockedStatic<LocalDate> now = mockStatic(LocalDate.class, CALLS_REAL_METHODS)) {
            now.when(LocalDate::now).thenReturn(PlanoPromptArquetipos.HOJE);
            return builder.buildOptimizedPrompt(atleta, meta, null,
                    PlanoPromptArquetipos.INICIO_SEMANA, arquetipo.diasEfetivos(), null, null, null, false);
        }
    }
}
