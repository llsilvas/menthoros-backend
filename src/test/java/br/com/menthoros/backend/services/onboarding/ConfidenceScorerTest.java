package br.com.menthoros.backend.services.onboarding;

import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.services.onboarding.impl.ConfidenceScorerImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ConfidenceScorerTest {

    private final ConfidenceScorer scorer = new ConfidenceScorerImpl();

    @Nested
    @DisplayName("calcular")
    class Calcular {

        @Test
        @DisplayName("score maximo (100) quando todos os 8 criterios sao satisfeitos plenamente")
        void scoreMaximoQuandoTudoSatisfeito() {
            ConfidenceScorerInput input = new ConfidenceScorerInput(
                    historicoDeSemanasComAtividadeSemanal(8, FonteDados.GARMIN, true, true),
                    true, 190, 50, new BigDecimal("4.50"), true, false, null
            );

            ConfidenceScoreResult resultado = scorer.calcular(input);

            assertThat(resultado.scoreBruto()).isEqualTo(100);
            assertThat(resultado.tier()).isEqualTo(ConfidenceTier.A);
        }

        @Test
        @DisplayName("score minimo (0) quando nenhum criterio e satisfeito")
        void scoreMinimoQuandoNadaSatisfeito() {
            ConfidenceScorerInput input = new ConfidenceScorerInput(
                    List.of(), false, null, null, null, false, false, null
            );

            ConfidenceScoreResult resultado = scorer.calcular(input);

            assertThat(resultado.scoreBruto()).isEqualTo(0);
            assertThat(resultado.tier()).isEqualTo(ConfidenceTier.C);
        }

        @Test
        @DisplayName("historico de 4 semanas vale metade dos 20 pontos do criterio (10 pts)")
        void historicoParcialProporcional() {
            ConfidenceScorerInput comQuatroSemanas = new ConfidenceScorerInput(
                    historicoDeSemanasComAtividadeSemanal(4, FonteDados.GARMIN, false, false),
                    false, null, null, null, false, false, null
            );
            ConfidenceScorerInput semHistorico = new ConfidenceScorerInput(
                    List.of(), false, null, null, null, false, false, null
            );

            ConfidenceScoreResult comHistorico = scorer.calcular(comQuatroSemanas);
            ConfidenceScoreResult semHistoricoResultado = scorer.calcular(semHistorico);

            assertThat(comHistorico.scoreBruto()).isGreaterThan(semHistoricoResultado.scoreBruto());
        }

        @Test
        @DisplayName("FC valida via fcMaxima/fcRepouso declarados, mesmo sem avgHR nas atividades")
        void fcValidaPorDeclaracao() {
            ConfidenceScorerInput input = new ConfidenceScorerInput(
                    List.of(), false, 190, 50, null, false, false, null
            );

            ConfidenceScoreResult resultado = scorer.calcular(input);

            assertThat(resultado.scoreBruto()).isEqualTo(10); // so o criterio FC valida (peso 10)
        }

        @Test
        @DisplayName("RPE registrado e proporcional a fracao de atividades com rpe nao-nulo")
        void rpeProporcional() {
            List<NormalizedActivity> metade = new ArrayList<>();
            metade.add(atividade(FonteDados.GARMIN, 7, LocalDate.now().minusDays(1)));
            metade.add(atividade(FonteDados.GARMIN, null, LocalDate.now().minusDays(2)));
            ConfidenceScorerInput input = new ConfidenceScorerInput(
                    metade, false, null, null, null, false, false, null
            );

            ConfidenceScoreResult resultado = scorer.calcular(input);

            // RPE e so 1 dos criterios com peso 10; nao deve ser nem 0 nem o maximo teorico isolado
            assertThat(resultado.scoreBruto()).isGreaterThan(0);
        }

        @Test
        @DisplayName("fonte confiavel: 100% Garmin (prioridade 1) vale mais que 100% Strava (prioridade 3)")
        void fonteConfiavelMaiorParaGarmin() {
            List<NormalizedActivity> comGarmin = List.of(atividade(FonteDados.GARMIN, null, LocalDate.now()));
            List<NormalizedActivity> comStrava = List.of(atividade(FonteDados.STRAVA, null, LocalDate.now()));

            ConfidenceScoreResult resultadoGarmin = scorer.calcular(new ConfidenceScorerInput(
                    comGarmin, false, null, null, null, false, false, null));
            ConfidenceScoreResult resultadoStrava = scorer.calcular(new ConfidenceScorerInput(
                    comStrava, false, null, null, null, false, false, null));

            assertThat(resultadoGarmin.scoreBruto()).isGreaterThan(resultadoStrava.scoreBruto());
        }

        @Test
        @DisplayName("tier A quando score >= 75")
        void tierAQuandoScoreAlto() {
            ConfidenceScorerInput input = new ConfidenceScorerInput(
                    historicoDeSemanasComAtividadeSemanal(8, FonteDados.GARMIN, true, true),
                    true, 190, 50, new BigDecimal("4.50"), false, false, null
            );

            ConfidenceScoreResult resultado = scorer.calcular(input);

            assertThat(resultado.tier()).isEqualTo(ConfidenceTier.A);
        }

        @Test
        @DisplayName("tier C quando score < 45")
        void tierCQuandoScoreBaixo() {
            ConfidenceScoreResult resultado = scorer.calcular(new ConfidenceScorerInput(
                    List.of(), false, null, null, null, false, false, null));

            assertThat(resultado.tier()).isEqualTo(ConfidenceTier.C);
        }

        @Test
        @DisplayName("bonus coach-como-proxy sobe C para B")
        void bonusCoachSobeCParaB() {
            ConfidenceScoreResult resultado = scorer.calcular(new ConfidenceScorerInput(
                    List.of(), false, null, null, null, false, true, null));

            assertThat(resultado.tier()).isEqualTo(ConfidenceTier.B);
            assertThat(resultado.bonusCoachAplicado()).isTrue();
        }

        @Test
        @DisplayName("bonus coach-como-proxy nao sobe tier A (ja no teto)")
        void bonusCoachNaoUltrapassaTierA() {
            ConfidenceScorerInput input = new ConfidenceScorerInput(
                    historicoDeSemanasComAtividadeSemanal(8, FonteDados.GARMIN, true, true),
                    true, 190, 50, new BigDecimal("4.50"), true, true, null
            );

            ConfidenceScoreResult resultado = scorer.calcular(input);

            assertThat(resultado.tier()).isEqualTo(ConfidenceTier.A);
        }

        @Test
        @DisplayName("bonus coach-como-proxy nunca desce o tier")
        void bonusCoachNuncaDesce() {
            ConfidenceScoreResult semBonus = scorer.calcular(new ConfidenceScorerInput(
                    List.of(), false, null, null, null, false, false, null));
            ConfidenceScoreResult comBonus = scorer.calcular(new ConfidenceScorerInput(
                    List.of(), false, null, null, null, false, true, null));

            assertThat(comBonus.scoreBruto()).isEqualTo(semBonus.scoreBruto()); // score bruto inalterado
            assertThat(comBonus.tier().ordinal()).isLessThanOrEqualTo(semBonus.tier().ordinal());
        }

        @Test
        @DisplayName("sem historico, dispositivoMarca GARMIN vale o criterio Fonte confiavel como prior (retrofit 10.6)")
        void dispositivoMarcaComoPriorSemHistorico() {
            ConfidenceScorerInput input = new ConfidenceScorerInput(
                    List.of(), false, null, null, null, false, false, br.com.menthoros.backend.enums.DispositivoMarca.GARMIN);

            ConfidenceScoreResult resultado = scorer.calcular(input);

            assertThat(resultado.scoreBruto()).isEqualTo(15); // so o criterio Fonte confiavel (peso 15)
        }

        @Test
        @DisplayName("sem historico, dispositivoMarca OUTRO (baixa prioridade) nao pontua o criterio Fonte confiavel")
        void dispositivoMarcaBaixaPrioridadeNaoPontua() {
            ConfidenceScorerInput input = new ConfidenceScorerInput(
                    List.of(), false, null, null, null, false, false, br.com.menthoros.backend.enums.DispositivoMarca.OUTRO);

            ConfidenceScoreResult resultado = scorer.calcular(input);

            assertThat(resultado.scoreBruto()).isEqualTo(0);
        }

        @Test
        @DisplayName("com historico real, dispositivoMarca e ignorado (dado real sempre substitui o prior)")
        void historicoRealSubstituiPriorDeDispositivo() {
            List<NormalizedActivity> comStrava = List.of(atividade(FonteDados.STRAVA, null, LocalDate.now()));
            ConfidenceScorerInput comPriorGarmin = new ConfidenceScorerInput(
                    comStrava, false, null, null, null, false, false, br.com.menthoros.backend.enums.DispositivoMarca.GARMIN);
            ConfidenceScorerInput semPrior = new ConfidenceScorerInput(
                    comStrava, false, null, null, null, false, false, null);

            ConfidenceScoreResult resultadoComPrior = scorer.calcular(comPriorGarmin);
            ConfidenceScoreResult resultadoSemPrior = scorer.calcular(semPrior);

            // FonteDados.STRAVA (prioridade 3) e o dado real ja disponivel — o prior GARMIN e ignorado
            assertThat(resultadoComPrior.scoreBruto()).isEqualTo(resultadoSemPrior.scoreBruto());
        }

        @Test
        @DisplayName("lanca IllegalArgumentException quando input e null")
        void lancaExcecaoQuandoInputNulo() {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> scorer.calcular(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    private List<NormalizedActivity> historicoDeSemanasComAtividadeSemanal(
            int semanas, FonteDados fonte, boolean comRpe, boolean comFcAlta) {
        List<NormalizedActivity> historico = new ArrayList<>();
        for (int i = 1; i <= semanas; i++) {
            LocalDate data = LocalDate.now().minusWeeks(i);
            NormalizedActivity atividade = new NormalizedActivity(
                    UUID.randomUUID(), "a" + i, UUID.randomUUID(), data, Sport.RUNNING,
                    45, 10.0,
                    comFcAlta ? 150 : null, comFcAlta ? 170 : null,
                    Duration.ofSeconds(270), null,
                    comRpe ? 6 : null,
                    fonte, 0.9
            );
            historico.add(atividade);
        }
        return historico;
    }

    private NormalizedActivity atividade(FonteDados fonte, Integer rpe, LocalDate data) {
        return new NormalizedActivity(
                UUID.randomUUID(), "a1", UUID.randomUUID(), data, Sport.RUNNING,
                45, 10.0, null, null, Duration.ofSeconds(270), null, rpe, fonte, 0.9
        );
    }
}
