package br.com.menthoros.backend.domain.planner;

import br.com.menthoros.backend.enums.NivelExperiencia;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InjuryPolicyResolverTest {

    private final InjuryPolicyResolver resolver = new InjuryPolicyResolver();
    private final LocalDate referencia = LocalDate.of(2026, 3, 9);
    private static final int JANELA_PADRAO_DIAS = 30;

    @Nested
    @DisplayName("resolve — lesao ativa (CA4)")
    class LesaoAtiva {

        @Test
        @DisplayName("temLesao=true forca fase RECOVERY e requiresCoachReview, independente do calendario")
        void temLesaoForcaRecoveryEReview() {
            AthleteSnapshot atleta = atleta(true, null, null);

            InjuryPolicyResult resultado = resolver.resolve(atleta, referencia, JANELA_PADRAO_DIAS);

            assertThat(resultado.phaseOverride()).contains(TrainingPhase.RECOVERY);
            assertThat(resultado.requiresCoachReview()).isTrue();
        }
    }

    @Nested
    @DisplayName("resolve — lesao recente (CA14)")
    class LesaoRecente {

        @Test
        @DisplayName("dataUltimaLesao dentro da janela configurada -> RETURN_TO_TRAINING")
        void dataRecenteDentroDaJanelaRetornoAoTreino() {
            AthleteSnapshot atleta = atleta(false, null, referencia.minusDays(10));

            InjuryPolicyResult resultado = resolver.resolve(atleta, referencia, JANELA_PADRAO_DIAS);

            assertThat(resultado.phaseOverride()).contains(TrainingPhase.RETURN_TO_TRAINING);
        }

        @Test
        @DisplayName("dataUltimaLesao fora da janela configurada nao gera override")
        void dataForaDaJanelaNaoGeraOverride() {
            AthleteSnapshot atleta = atleta(false, null, referencia.minusDays(45));

            InjuryPolicyResult resultado = resolver.resolve(atleta, referencia, JANELA_PADRAO_DIAS);

            assertThat(resultado.phaseOverride()).isEmpty();
        }

        @Test
        @DisplayName("janela configuravel: 40 dias atras esta dentro de uma janela de 60 dias")
        void janelaConfiguravelMaiorAmpliaOAlcance() {
            AthleteSnapshot atleta = atleta(false, null, referencia.minusDays(40));

            InjuryPolicyResult resultado = resolver.resolve(atleta, referencia, 60);

            assertThat(resultado.phaseOverride()).contains(TrainingPhase.RETURN_TO_TRAINING);
        }
    }

    @Nested
    @DisplayName("resolve — descricao sem flag/data (design.md Decisao 14)")
    class DescricaoSemEstrutura {

        @Test
        @DisplayName("descricaoLesao preenchida sem temLesao/dataUltimaLesao -> review sem NLP")
        void descricaoPreenchidaSemFlagGeraReviewSemAlterarFase() {
            AthleteSnapshot atleta = atleta(false, "sentindo uma dor no joelho direito", null);

            InjuryPolicyResult resultado = resolver.resolve(atleta, referencia, JANELA_PADRAO_DIAS);

            assertThat(resultado.phaseOverride()).isEmpty();
            assertThat(resultado.requiresCoachReview()).isTrue();
            assertThat(resultado.reason()).contains("INJURY_DESCRIPTION_UNSTRUCTURED");
        }
    }

    @Nested
    @DisplayName("resolve — sem dados de lesao")
    class SemDadosDeLesao {

        @Test
        @DisplayName("sem temLesao, dataUltimaLesao ou descricaoLesao -> fluxo normal")
        void semDadosFluxoNormal() {
            AthleteSnapshot atleta = atleta(false, null, null);

            InjuryPolicyResult resultado = resolver.resolve(atleta, referencia, JANELA_PADRAO_DIAS);

            assertThat(resultado.phaseOverride()).isEmpty();
            assertThat(resultado.requiresCoachReview()).isFalse();
        }
    }

    private AthleteSnapshot atleta(boolean temLesao, String descricaoLesao, LocalDate dataUltimaLesao) {
        return new AthleteSnapshot(UUID.randomUUID(), NivelExperiencia.INTERMEDIARIO, temLesao, descricaoLesao, dataUltimaLesao, List.of(), null);
    }
}
