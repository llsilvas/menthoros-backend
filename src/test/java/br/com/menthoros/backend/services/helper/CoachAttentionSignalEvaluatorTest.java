package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.enums.MotivoAtencao;
import br.com.menthoros.backend.enums.Severidade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
        @DisplayName("TSB ≤ -35 (fadiga excessiva) → CRITICA")
        void critica() {
            var sinal = evaluator.avaliarFadiga(-40.0).orElseThrow();
            assertThat(sinal.motivo()).isEqualTo(MotivoAtencao.FADIGA);
            assertThat(sinal.severidade()).isEqualTo(Severidade.CRITICA);
            assertThat(sinal.evidencias()).singleElement()
                    .satisfies(e -> assertThat(e.label()).isEqualTo("TSB"));
        }

        @Test
        @DisplayName("TSB em (-35,-30] (fadiga alta) → ALTA")
        void alta() {
            var sinal = evaluator.avaliarFadiga(-32.0).orElseThrow();
            assertThat(sinal.severidade()).isEqualTo(Severidade.ALTA);
        }

        @Test
        @DisplayName("TSB em (-30,-10] (fadiga moderada) → MEDIA")
        void media() {
            var sinal = evaluator.avaliarFadiga(-15.0).orElseThrow();
            assertThat(sinal.severidade()).isEqualTo(Severidade.MEDIA);
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
        @DisplayName("sobrecarga → ALTA")
        void sobrecargaAlta() {
            var sinal = evaluator.avaliarSobrecarga(true, false, false, false, null).orElseThrow();
            assertThat(sinal.motivo()).isEqualTo(MotivoAtencao.SOBRECARGA);
            assertThat(sinal.severidade()).isEqualTo(Severidade.ALTA);
        }

        @Test
        @DisplayName("necessita descanso → ALTA")
        void necessitaDescansoAlta() {
            assertThat(evaluator.avaliarSobrecarga(false, true, false, false, null).orElseThrow()
                    .severidade()).isEqualTo(Severidade.ALTA);
        }

        @Test
        @DisplayName("apenas ramp/dias consecutivos → MEDIA com contagem na evidência")
        void rampMedia() {
            var sinal = evaluator.avaliarSobrecarga(false, false, true, true, 6).orElseThrow();
            assertThat(sinal.severidade()).isEqualTo(Severidade.MEDIA);
            assertThat(sinal.evidencias()).anySatisfy(e ->
                    assertThat(e.value()).isEqualTo("6"));
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
        @DisplayName("2 perdidos → MEDIA")
        void dois() {
            assertThat(evaluator.avaliarAderencia(2).orElseThrow().severidade()).isEqualTo(Severidade.MEDIA);
        }

        @Test
        @DisplayName("3 perdidos (corte) → ALTA")
        void tres() {
            assertThat(evaluator.avaliarAderencia(3).orElseThrow().severidade()).isEqualTo(Severidade.ALTA);
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
        @DisplayName("7 dias (corte) → MEDIA")
        void sete() {
            assertThat(evaluator.avaliarInatividade(7L).orElseThrow().severidade()).isEqualTo(Severidade.MEDIA);
        }

        @Test
        @DisplayName("13 dias → MEDIA")
        void treze() {
            assertThat(evaluator.avaliarInatividade(13L).orElseThrow().severidade()).isEqualTo(Severidade.MEDIA);
        }

        @Test
        @DisplayName("14 dias (corte) → ALTA")
        void catorze() {
            assertThat(evaluator.avaliarInatividade(14L).orElseThrow().severidade()).isEqualTo(Severidade.ALTA);
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
        @DisplayName("vencidas → MEDIA")
        void vencidas() {
            var sinal = evaluator.avaliarZonasVencidas(true).orElseThrow();
            assertThat(sinal.motivo()).isEqualTo(MotivoAtencao.ZONAS_VENCIDAS);
            assertThat(sinal.severidade()).isEqualTo(Severidade.MEDIA);
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
        @DisplayName("sem plano → SEM_PLANO (ALTA)")
        void semPlano() {
            var sinal = evaluator.avaliarSemPlano(false).orElseThrow();
            assertThat(sinal.motivo()).isEqualTo(MotivoAtencao.SEM_PLANO);
            assertThat(sinal.severidade()).isEqualTo(Severidade.ALTA);
        }
    }
}
