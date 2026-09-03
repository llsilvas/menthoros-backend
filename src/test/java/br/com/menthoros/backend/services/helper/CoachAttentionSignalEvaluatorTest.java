package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.enums.MotivoAtencao;
import br.com.menthoros.backend.enums.MotivoRevisaoProva;
import br.com.menthoros.backend.enums.Severidade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CoachAttentionSignalEvaluator")
class CoachAttentionSignalEvaluatorTest {

    private final CoachAttentionSignalEvaluator evaluator = new CoachAttentionSignalEvaluator();

    @Nested
    @DisplayName("avaliarFadiga")
    class AvaliarFadiga {

        @Test
        @DisplayName("TSB nulo → sem sinal")
        void tsbNulo() {
            assertThat(evaluator.avaliarFadiga(null)).isEmpty();
        }

        @Test
        @DisplayName("TSB ≤ -35 (fadiga excessiva) → CRITICA com rationale e sourceRules")
        void critica() {
            var sinal = evaluator.avaliarFadiga(-40.0).orElseThrow();
            assertThat(sinal.motivo()).isEqualTo(MotivoAtencao.FADIGA);
            assertThat(sinal.severidade()).isEqualTo(Severidade.CRITICA);
            assertThat(sinal.evidencias()).singleElement()
                    .satisfies(e -> assertThat(e.label()).isEqualTo("TSB"));
            assertThat(sinal.rationale()).contains("TSB em -40.0").contains("FADIGA_EXCESSIVA").contains("overtraining");
            assertThat(sinal.sourceRules())
                    .contains("CoachAttentionSignalEvaluator.avaliarFadiga")
                    .contains("FaixaTsb.FADIGA_EXCESSIVA");
        }

        @Test
        @DisplayName("TSB em (-35,-30] (fadiga alta) → ALTA com rationale de fadiga elevada (não overtraining)")
        void alta() {
            var sinal = evaluator.avaliarFadiga(-32.0).orElseThrow();
            assertThat(sinal.severidade()).isEqualTo(Severidade.ALTA);
            assertThat(sinal.rationale()).contains("-32.0").contains("FADIGA_ALTA").contains("fadiga elevada");
            assertThat(sinal.rationale()).doesNotContain("overtraining");
            assertThat(sinal.sourceRules()).contains("FaixaTsb.FADIGA_ALTA");
        }

        @Test
        @DisplayName("TSB em (-30,-10] (fadiga moderada) → MEDIA com rationale mencionando ATENCAO")
        void media() {
            var sinal = evaluator.avaliarFadiga(-15.0).orElseThrow();
            assertThat(sinal.severidade()).isEqualTo(Severidade.MEDIA);
            assertThat(sinal.rationale()).contains("-15.0").contains("ACUMULANDO_FADIGA");
            assertThat(sinal.sourceRules()).contains("FaixaTsb.ACUMULANDO_FADIGA");
        }

        @Test
        @DisplayName("TSB em forma ideal/recuperando (INFO) → sem sinal")
        void formaIdeal() {
            assertThat(evaluator.avaliarFadiga(5.0)).isEmpty();
        }
    }

    @Nested
    @DisplayName("avaliarSobrecarga")
    class AvaliarSobrecarga {

        @Test
        @DisplayName("sem flags → sem sinal")
        void semFlags() {
            assertThat(evaluator.avaliarSobrecarga(false, false, false, false, null)).isEmpty();
        }

        @Test
        @DisplayName("sobrecarga → ALTA com rationale de sobrecarga e sourceRules corretos")
        void sobrecargaAlta() {
            var sinal = evaluator.avaliarSobrecarga(true, false, false, false, null).orElseThrow();
            assertThat(sinal.motivo()).isEqualTo(MotivoAtencao.SOBRECARGA);
            assertThat(sinal.severidade()).isEqualTo(Severidade.ALTA);
            assertThat(sinal.rationale()).contains("sobrecarga ativa");
            assertThat(sinal.sourceRules())
                    .contains("CoachAttentionSignalEvaluator.avaliarSobrecarga")
                    .contains("PlanoMetaDados.alertaSobrecarga");
        }

        @Test
        @DisplayName("necessita descanso → ALTA com rationale de descanso como flag prioritário")
        void necessitaDescansoAlta() {
            var sinal = evaluator.avaliarSobrecarga(false, true, false, false, null).orElseThrow();
            assertThat(sinal.severidade()).isEqualTo(Severidade.ALTA);
            assertThat(sinal.rationale()).contains("descanso");
            assertThat(sinal.sourceRules()).contains("PlanoMetaDados.alertaNecessitaDescanso");
        }

        @Test
        @DisplayName("sobrecarga + descanso → rationale prioriza sobrecarga; sourceRules inclui ambos")
        void sobrecargaEDescanso() {
            var sinal = evaluator.avaliarSobrecarga(true, true, false, false, null).orElseThrow();
            assertThat(sinal.rationale()).contains("sobrecarga ativa");
            assertThat(sinal.sourceRules())
                    .contains("PlanoMetaDados.alertaSobrecarga")
                    .contains("PlanoMetaDados.alertaNecessitaDescanso");
        }

        @Test
        @DisplayName("apenas ramp/dias consecutivos → MEDIA com contagem na evidência e sourceRules correspondentes")
        void rampMedia() {
            var sinal = evaluator.avaliarSobrecarga(false, false, true, true, 6).orElseThrow();
            assertThat(sinal.severidade()).isEqualTo(Severidade.MEDIA);
            assertThat(sinal.evidencias()).anySatisfy(e -> assertThat(e.value()).isEqualTo("6"));
            assertThat(sinal.rationale()).contains("rampa");
            assertThat(sinal.sourceRules())
                    .contains("PlanoMetaDados.alertaRampAlto")
                    .contains("PlanoMetaDados.alertaDiasConsecutivos");
        }
    }

    @Nested
    @DisplayName("avaliarAderencia")
    class AvaliarAderencia {

        @Test
        @DisplayName("zero perdidos → sem sinal")
        void zero() {
            assertThat(evaluator.avaliarAderencia(0)).isEmpty();
        }

        @Test
        @DisplayName("2 perdidos → MEDIA com rationale e sourceRules de aderência")
        void dois() {
            var sinal = evaluator.avaliarAderencia(2).orElseThrow();
            assertThat(sinal.severidade()).isEqualTo(Severidade.MEDIA);
            assertThat(sinal.rationale()).contains("2 treino").contains("14 dias");
            assertThat(sinal.sourceRules())
                    .contains("CoachAttentionSignalEvaluator.avaliarAderencia")
                    .contains("TreinoExecucaoStatus.PERDIDO|PARCIAL");
        }

        @Test
        @DisplayName("3 perdidos (corte) → ALTA com contagem no rationale")
        void tres() {
            var sinal = evaluator.avaliarAderencia(3).orElseThrow();
            assertThat(sinal.severidade()).isEqualTo(Severidade.ALTA);
            assertThat(sinal.rationale()).contains("3 treino");
        }
    }

    @Nested
    @DisplayName("avaliarInatividade")
    class AvaliarInatividade {

        @Test
        @DisplayName("nulo → sem sinal")
        void nulo() {
            assertThat(evaluator.avaliarInatividade(null)).isEmpty();
        }

        @Test
        @DisplayName("6 dias → sem sinal")
        void seis() {
            assertThat(evaluator.avaliarInatividade(6L)).isEmpty();
        }

        @Test
        @DisplayName("7 dias (corte) → MEDIA com rationale e sourceRules de inatividade")
        void sete() {
            var sinal = evaluator.avaliarInatividade(7L).orElseThrow();
            assertThat(sinal.severidade()).isEqualTo(Severidade.MEDIA);
            assertThat(sinal.rationale()).contains("7 dias");
            assertThat(sinal.sourceRules()).contains("CoachAttentionSignalEvaluator.avaliarInatividade");
        }

        @Test
        @DisplayName("13 dias → MEDIA")
        void treze() {
            assertThat(evaluator.avaliarInatividade(13L).orElseThrow().severidade()).isEqualTo(Severidade.MEDIA);
        }

        @Test
        @DisplayName("14 dias (corte) → ALTA com contagem no rationale")
        void catorze() {
            var sinal = evaluator.avaliarInatividade(14L).orElseThrow();
            assertThat(sinal.severidade()).isEqualTo(Severidade.ALTA);
            assertThat(sinal.rationale()).contains("14 dias");
        }
    }

    @Nested
    @DisplayName("avaliarZonasVencidas")
    class AvaliarZonasVencidas {

        @Test
        @DisplayName("em dia → sem sinal")
        void emDia() {
            assertThat(evaluator.avaliarZonasVencidas(false)).isEmpty();
        }

        @Test
        @DisplayName("vencidas → MEDIA com rationale e sourceRules de zonas")
        void vencidas() {
            var sinal = evaluator.avaliarZonasVencidas(true).orElseThrow();
            assertThat(sinal.motivo()).isEqualTo(MotivoAtencao.ZONAS_VENCIDAS);
            assertThat(sinal.severidade()).isEqualTo(Severidade.MEDIA);
            assertThat(sinal.rationale()).contains("3 meses").contains("zonas");
            assertThat(sinal.sourceRules())
                    .contains("CoachAttentionSignalEvaluator.avaliarZonasVencidas")
                    .contains("Atleta.precisaAtualizarTestes");
        }
    }

    @Nested
    @DisplayName("avaliarSemPlano")
    class AvaliarSemPlano {

        @Test
        @DisplayName("com plano → sem sinal")
        void comPlano() {
            assertThat(evaluator.avaliarSemPlano(true)).isEmpty();
        }

        @Test
        @DisplayName("sem plano → SEM_PLANO (ALTA) com rationale e sourceRules")
        void semPlano() {
            var sinal = evaluator.avaliarSemPlano(false).orElseThrow();
            assertThat(sinal.motivo()).isEqualTo(MotivoAtencao.SEM_PLANO);
            assertThat(sinal.severidade()).isEqualTo(Severidade.ALTA);
            assertThat(sinal.rationale()).contains("sem plano ativo");
            assertThat(sinal.sourceRules()).contains("CoachAttentionSignalEvaluator.avaliarSemPlano");
        }
    }

    @Nested
    @DisplayName("avaliarProvaPendente")
    class AvaliarProvaPendente {

        @Test
        @DisplayName("lista vazia ou nula → sem sinal")
        void semPendentes() {
            assertThat(evaluator.avaliarProvaPendente(List.of())).isEmpty();
            assertThat(evaluator.avaliarProvaPendente(null)).isEmpty();
        }

        @Test
        @DisplayName("prova nova dentro do prazo → ALTA com evidências de prova, data, distância, preparação e motivo")
        void novaDentroDoPrazo() {
            var sinal = evaluator.avaliarProvaPendente(List.of(
                    pendente("Maratona SP", 20, 16, false, MotivoRevisaoProva.NOVA, null))).orElseThrow();

            assertThat(sinal.motivo()).isEqualTo(MotivoAtencao.PROVA_ATLETA);
            assertThat(sinal.severidade()).isEqualTo(Severidade.ALTA);
            assertThat(sinal.evidencias()).extracting(e -> e.label())
                    .containsExactly("Prova", "Data", "Distância", "Preparação", "Motivo");
            assertThat(sinal.evidencias().get(0).value()).isEqualTo("Maratona SP");
            assertThat(sinal.evidencias().get(3).value()).isEqualTo("20 de 16 semanas");
            assertThat(sinal.evidencias().get(4).value()).isEqualTo("prova nova");
            assertThat(sinal.sourceRules()).contains("CoachAttentionSignalEvaluator.avaliarProvaPendente");
        }

        @Test
        @DisplayName("preparação curta → CRITICA com '8 de 16 semanas'")
        void preparacaoCurta() {
            var sinal = evaluator.avaliarProvaPendente(List.of(
                    pendente("Maratona SP", 8, 16, true, MotivoRevisaoProva.NOVA, null))).orElseThrow();

            assertThat(sinal.severidade()).isEqualTo(Severidade.CRITICA);
            assertThat(sinal.evidencias().get(3).value()).isEqualTo("8 de 16 semanas");
            assertThat(sinal.rationale()).contains("preparação curta");
        }

        @Test
        @DisplayName("troca de alvo → CRITICA com o nome da alvo anterior no motivo")
        void trocaDeAlvo() {
            var sinal = evaluator.avaliarProvaPendente(List.of(
                    pendente("Maratona SP", 20, 16, false, MotivoRevisaoProva.ALVO_TROCADA, "Meia do Rio"))).orElseThrow();

            assertThat(sinal.severidade()).isEqualTo(Severidade.CRITICA);
            assertThat(sinal.evidencias().get(4).value()).isEqualTo("alvo trocada: antes Meia do Rio");
        }

        @Test
        @DisplayName("duas provas pendentes → evidências das duas")
        void duasProvas() {
            var sinal = evaluator.avaliarProvaPendente(List.of(
                    pendente("A", 20, 16, false, MotivoRevisaoProva.NOVA, null),
                    pendente("B", 10, 12, false, MotivoRevisaoProva.CANCELADA, null))).orElseThrow();

            assertThat(sinal.evidencias()).hasSize(10);
            assertThat(sinal.severidade()).isEqualTo(Severidade.ALTA);
        }

        private ProvaPendenteSinal pendente(String nome, int faltando, int minimo, boolean curta,
                                            MotivoRevisaoProva motivo, String alvoAnterior) {
            return new ProvaPendenteSinal(nome, java.time.LocalDate.of(2026, 12, 6), "42 KM",
                    faltando, minimo, curta, motivo, alvoAnterior);
        }
    }
}
