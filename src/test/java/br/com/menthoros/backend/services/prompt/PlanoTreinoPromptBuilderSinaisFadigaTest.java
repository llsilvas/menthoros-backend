package br.com.menthoros.backend.services.prompt;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.enums.FatigueSignalType;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.services.helper.FatigueSignal;
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

/**
 * O {@code PromptGerado} carrega os sinais de fadiga até o {@code IaServiceImpl}
 * (add-descanso-explicito-por-fadiga, task 1.2) — é deles que a regra de cobertura decide se um dia
 * de descanso é legítimo. Calculados uma vez por geração, junto do prompt.
 */
@DisplayName("PlanoTreinoPromptBuilder — sinais de fadiga no PromptGerado")
class PlanoTreinoPromptBuilderSinaisFadigaTest {

    @Test
    @DisplayName("atleta com TSB abaixo do limiar do nível: o sinal chega no PromptGerado com valor e limiar")
    void sinaisChegamNoPromptGerado() {
        var gerado = montar(atleta(NivelExperiencia.INTERMEDIARIO), meta(-18.0));

        assertThat(gerado.cobertura().fatigueSignals()).extracting(FatigueSignal::type)
                .contains(FatigueSignalType.TSB_BAIXO);
        var tsb = gerado.cobertura().fatigueSignals().stream()
                .filter(s -> s.type() == FatigueSignalType.TSB_BAIXO).findFirst().orElseThrow();
        assertThat(tsb.value()).isEqualTo(-18.0);
        assertThat(tsb.threshold()).isEqualTo(-15.0);
    }

    @Test
    @DisplayName("atleta sem fadiga: lista vazia, nunca null")
    void semFadigaListaVazia() {
        var gerado = montar(atleta(NivelExperiencia.INTERMEDIARIO), meta(5.0));

        assertThat(gerado.cobertura().fatigueSignals()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("CA13: com skeleton do planner o contexto é nulo e o bloco de cobertura não é escrito")
    void comSkeletonNaoHaCobertura() {
        var skeleton = org.mockito.Mockito.mock(br.com.menthoros.backend.domain.planner.WeekPlanSkeleton.class);

        var gerado = montar(atleta(NivelExperiencia.INTERMEDIARIO), meta(-18.0), skeleton);

        assertThat(gerado.cobertura()).isNull();
        assertThat(gerado.user()).doesNotContain("COBERTURA DA SEMANA");
    }

    @Test
    @DisplayName("sem skeleton, o bloco de cobertura é escrito no prompt")
    void semSkeletonEscreveBloco() {
        var gerado = montar(atleta(NivelExperiencia.INTERMEDIARIO), meta(-18.0));

        assertThat(gerado.cobertura()).isNotNull();
        assertThat(gerado.user()).contains("COBERTURA DA SEMANA");
    }

    private PlanoTreinoPromptBuilder.PromptGerado montar(Atleta atleta, PlanoMetaDados meta) {
        return montar(atleta, meta, null);
    }

    private PlanoTreinoPromptBuilder.PromptGerado montar(Atleta atleta, PlanoMetaDados meta,
                                                         br.com.menthoros.backend.domain.planner.WeekPlanSkeleton skeleton) {
        var arquetipo = PlanoPromptArquetipos.todos().getFirst();
        TreinoHistoricoProvider provider = mock(TreinoHistoricoProvider.class);
        when(provider.prepararContexto(any())).thenReturn(arquetipo.contexto());
        PlanoTreinoPromptBuilder builder = PlanoPromptArquetipos.builder(provider);

        try (MockedStatic<LocalDate> now = mockStatic(LocalDate.class, CALLS_REAL_METHODS)) {
            now.when(LocalDate::now).thenReturn(PlanoPromptArquetipos.HOJE);
            return builder.buildOptimizedPrompt(atleta, meta, null,
                    PlanoPromptArquetipos.INICIO_SEMANA, arquetipo.diasEfetivos(), null, null, skeleton, false);
        }
    }

    private static Atleta atleta(NivelExperiencia nivel) {
        return PlanoPromptArquetipos.todos().getFirst().atleta().toBuilder()
                .nivelExperiencia(nivel)
                .build();
    }

    private static PlanoMetaDados meta(double tsbProntidao) {
        PlanoMetaDados m = PlanoPromptArquetipos.todos().getFirst().meta();
        m.setTsbProntidaoAtual(tsbProntidao);
        m.setCtlAtual(40.0);
        m.setAlertaDiasConsecutivos(false);
        return m;
    }
}
