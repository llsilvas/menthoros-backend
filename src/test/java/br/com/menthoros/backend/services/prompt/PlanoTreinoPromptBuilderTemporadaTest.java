package br.com.menthoros.backend.services.prompt;

import br.com.menthoros.backend.domain.planner.SeasonWeekSnapshot;
import br.com.menthoros.backend.domain.planner.TrainingPhase;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.enums.DistanciaProva;
import br.com.menthoros.backend.enums.ProvaStatus;
import br.com.menthoros.backend.services.helper.TreinoHistoricoProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Prompt com semana da temporada ativa (add-season-plan-weekly-context, 3.2). Sem temporada o prompt é
 * o legado byte a byte — coberto pelos dois goldens do {@code PlanoTreinoPromptBuilderGoldenTest}.
 */
@DisplayName("PlanoTreinoPromptBuilder — semana da temporada")
class PlanoTreinoPromptBuilderTemporadaTest {

    private static Locale localeOriginal;

    @BeforeAll
    static void fixarLocale() {
        localeOriginal = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("pt-BR"));
    }

    @AfterAll
    static void restaurarLocale() {
        Locale.setDefault(localeOriginal);
    }

    @Nested
    @DisplayName("buildOptimizedPrompt")
    class BuildOptimizedPrompt {

        @Test
        @DisplayName("troca a seção de periodização legada pelo bloco de posição no ciclo")
        void blocoNoLugarDaSecaoLegada() {
            String user = montar(atletaBase(), meta(0.0, 0), temporada());

            assertThat(user)
                    .contains("POSIÇÃO NO CICLO DA TEMPORADA")
                    .contains("Semana 5 de 16")
                    .contains("Próximo marco:** Meia de Carnaval, 14/02/2026")
                    .doesNotContain("PERIODIZAÇÃO E FASE ATUAL");
        }

        @Test
        @DisplayName("sem fadiga, o TSS Alvo Semanal é o targetTss da semana, não ctlAlvo * 7")
        void alvoDaTemporada() {
            PlanoMetaDados meta = meta(0.0, 0);
            int legado = new PeriodizacaoPromptFormatter().calcularTssAlvo(meta);

            String user = montar(atletaBase(), meta, temporada());

            assertThat(legado).isNotEqualTo(400);
            assertThat(user).contains("**TSS Alvo Semanal:** 400 pontos\n")
                    .doesNotContain("**TSS Alvo Semanal:** " + legado + " pontos");
        }

        @Test
        @DisplayName("fadiga moderada aplica ×0,75 sobre o alvo da temporada")
        void fadigaModerada() {
            String user = montar(atletaBase(), meta(-25.0, 0), temporada());

            assertThat(user).contains("**TSS Alvo Semanal:** 300 pontos (reduzido 25% por semana regenerativa)");
        }

        @Test
        @DisplayName("sobrecarga aplica ×0,55 sobre o alvo da temporada")
        void sobrecarga() {
            String user = montar(atletaBase(), meta(-35.0, 0), temporada());

            assertThat(user).contains("**TSS Alvo Semanal:** 220 pontos (reduzido 45% por semana regenerativa)");
        }

        @Test
        @DisplayName("progressão contínua em alerta aplica ×0,55 sobre o alvo da temporada")
        void progressaoContinua() {
            String user = montar(atletaBase(), meta(0.0, 4), temporada());

            assertThat(user).contains("**TSS Alvo Semanal:** 220 pontos");
        }

        @Test
        @DisplayName("prova no sábado anterior: o bloco declara que o pós-prova prevalece")
        void posProvaPrevalece() {
            Prova sabado = Prova.builder().id(UUID.randomUUID()).nomeProva("10K de Verão")
                    .dataProva(PlanoPromptArquetipos.INICIO_SEMANA.minusDays(2)).distancia(DistanciaProva.KM_10)
                    .statusProva(ProvaStatus.CONCLUIDA).build();
            Atleta atleta = atletaBase().toBuilder().provas(List.of(sabado)).build();

            String user = montar(atleta, meta(0.0, 0), temporada());

            assertThat(user).contains("fase de PÓS-PROVA").contains("prevalece sobre a fase da temporada");
        }

        @Test
        @DisplayName("lesão ativa: o bloco declara que a fase de lesão prevalece")
        void lesaoPrevalece() {
            Atleta atleta = atletaBase().toBuilder().temLesao(true).build();

            String user = montar(atleta, meta(0.0, 0), temporada());

            assertThat(user).contains("fase de LESÃO ATIVA").contains("prevalece sobre a fase da temporada");
        }

        @Test
        @DisplayName("sem lesão nem pós-prova, nada prevalece sobre a temporada")
        void nadaPrevalece() {
            String user = montar(atletaBase(), meta(0.0, 0), temporada());

            assertThat(user).doesNotContain("prevalece sobre a fase da temporada");
        }
    }

    private static String montar(Atleta atleta, PlanoMetaDados meta, SeasonWeekSnapshot temporada) {
        var arquetipo = PlanoPromptArquetipos.todos().getFirst();
        TreinoHistoricoProvider provider = mock(TreinoHistoricoProvider.class);
        when(provider.prepararContexto(any())).thenReturn(arquetipo.contexto());
        PlanoTreinoPromptBuilder builder = PlanoPromptArquetipos.builder(provider);

        try (MockedStatic<LocalDate> now = mockStatic(LocalDate.class, CALLS_REAL_METHODS)) {
            now.when(LocalDate::now).thenReturn(PlanoPromptArquetipos.HOJE);
            return builder.buildOptimizedPrompt(atleta, meta, null, PlanoPromptArquetipos.INICIO_SEMANA,
                    arquetipo.diasEfetivos(), null, null, null, false, temporada).user();
        }
    }

    private static Atleta atletaBase() {
        return PlanoPromptArquetipos.todos().getFirst().atleta().toBuilder()
                .temLesao(false).descricaoLesao(null).dataUltimaLesao(null).provas(List.of())
                .build();
    }

    private static PlanoMetaDados meta(double tsb, int semanasProgressao) {
        PlanoMetaDados m = PlanoPromptArquetipos.todos().getFirst().meta();
        m.setCtlAtual(40.0);
        m.setTsbAtual(tsb);
        m.setSemanasProgressaoContinua(semanasProgressao);
        return m;
    }

    private static SeasonWeekSnapshot temporada() {
        return new SeasonWeekSnapshot(UUID.randomUUID(), 5, 16, TrainingPhase.BUILD, 400.0,
                new BigDecimal("42.000"), new BigDecimal("15.000"), false,
                Optional.of(new SeasonWeekSnapshot.Milestone("Meia de Carnaval", LocalDate.of(2026, 2, 14))));
    }
}
