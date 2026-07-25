package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.entity.PlanoSemanal;
import br.com.menthoros.backend.entity.RevisaoSemanal;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.enums.ConsumedReviewOutcome;
import br.com.menthoros.backend.enums.OrigemAprovacao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Heurística do desfecho (D9). Proxy de segunda ordem: mede se o plano foi mexido, não se o coach
 * concordou com o foco — os testes fixam esse contrato para que afrouxá-lo seja uma decisão
 * consciente, não um efeito colateral.
 */
class ConsumedReviewOutcomeResolverTest {

    private final ConsumedReviewOutcomeResolver resolver = new ConsumedReviewOutcomeResolver();

    @Nested
    @DisplayName("naAprovacao")
    class NaAprovacao {

        @Test
        @DisplayName("plano sem treino mexido → NO_ADJUSTMENT")
        void semAjuste() {
            PlanoSemanal plano = planoComRevisao(treino(false, false), treino(false, false));

            assertThat(resolver.naAprovacao(plano, OrigemAprovacao.COACH))
                    .isEqualTo(ConsumedReviewOutcome.NO_ADJUSTMENT);
        }

        @Test
        @DisplayName("treino editado pelo coach → ADJUSTED")
        void treinoEditado() {
            PlanoSemanal plano = planoComRevisao(treino(true, false), treino(false, false));

            assertThat(resolver.naAprovacao(plano, OrigemAprovacao.COACH))
                    .isEqualTo(ConsumedReviewOutcome.ADJUSTED);
        }

        @Test
        @DisplayName("treino adicionado pelo coach → ADJUSTED")
        void treinoAdicionado() {
            PlanoSemanal plano = planoComRevisao(treino(false, true));

            assertThat(resolver.naAprovacao(plano, OrigemAprovacao.COACH))
                    .isEqualTo(ConsumedReviewOutcome.ADJUSTED);
        }

        @Test
        @DisplayName("auto-approve → NO_COACH_IN_LOOP, nunca NO_ADJUSTMENT")
        void autoApproveNaoContaComoAceitacao() {
            PlanoSemanal plano = planoComRevisao(treino(false, false));

            assertThat(resolver.naAprovacao(plano, OrigemAprovacao.AUTO_CONFIANCA_ALTA))
                    .isEqualTo(ConsumedReviewOutcome.NO_COACH_IN_LOOP);
        }

        @Test
        @DisplayName("plano sem revisão consumida permanece NOT_CONSUMED")
        void semRevisaoConsumida() {
            PlanoSemanal plano = new PlanoSemanal();
            plano.setConsumedReviewOutcome(ConsumedReviewOutcome.NOT_CONSUMED);

            assertThat(resolver.naAprovacao(plano, OrigemAprovacao.COACH))
                    .isEqualTo(ConsumedReviewOutcome.NOT_CONSUMED);
        }

        @Test
        @DisplayName("plano sem treinos carregados não inventa ajuste")
        void semTreinos() {
            PlanoSemanal plano = planoComRevisao();
            plano.setTreinosPlanejados(null);

            assertThat(resolver.naAprovacao(plano, OrigemAprovacao.COACH))
                    .isEqualTo(ConsumedReviewOutcome.NO_ADJUSTMENT);
        }
    }

    @Nested
    @DisplayName("naRejeicao")
    class NaRejeicao {

        @Test
        @DisplayName("plano que consumiu revisão → PLAN_REJECTED")
        void rejeitado() {
            assertThat(resolver.naRejeicao(planoComRevisao(treino(false, false))))
                    .isEqualTo(ConsumedReviewOutcome.PLAN_REJECTED);
        }

        @Test
        @DisplayName("plano sem revisão consumida permanece NOT_CONSUMED")
        void rejeitadoSemRevisao() {
            PlanoSemanal plano = new PlanoSemanal();
            plano.setConsumedReviewOutcome(ConsumedReviewOutcome.NOT_CONSUMED);

            assertThat(resolver.naRejeicao(plano)).isEqualTo(ConsumedReviewOutcome.NOT_CONSUMED);
        }
    }

    private PlanoSemanal planoComRevisao(TreinoPlanejado... treinos) {
        PlanoSemanal plano = new PlanoSemanal();
        plano.setId(UUID.randomUUID());
        RevisaoSemanal revisao = RevisaoSemanal.builder().id(UUID.randomUUID()).build();
        plano.setConsumedReview(revisao);
        plano.setConsumedReviewOutcome(ConsumedReviewOutcome.PENDING);
        plano.setTreinosPlanejados(List.of(treinos));
        return plano;
    }

    private TreinoPlanejado treino(boolean editado, boolean adicionado) {
        TreinoPlanejado treino = new TreinoPlanejado();
        treino.setEditadoPeloCoach(editado);
        treino.setAdicionadoPeloCoach(adicionado);
        return treino;
    }
}
