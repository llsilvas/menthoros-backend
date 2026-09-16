package br.com.menthoros.backend.services.helper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EvalJudgeCalibration")
class EvalJudgeCalibrationTest {

    private final EvalJudgeCalibration calibracao = new EvalJudgeCalibration();

    @Nested
    @DisplayName("avaliar")
    class Avaliar {

        @Test
        @DisplayName("menos de 20 casos — sempre não calibrado, mesmo com 100% de concordância")
        void menosDe20CasosNuncaCalibrado() {
            var pares = List.of(
                    new EvalJudgeCalibration.ParNotas(5, 5),
                    new EvalJudgeCalibration.ParNotas(1, 1));

            var resultado = calibracao.avaliar(pares);

            assertThat(resultado.calibrado()).isFalse();
            assertThat(resultado.totalCasos()).isEqualTo(2);
        }

        @Test
        @DisplayName("0 casos — não calibrado, taxa 0, sem lançar")
        void zeroCasosNaoLanca() {
            var resultado = calibracao.avaliar(List.of());

            assertThat(resultado.calibrado()).isFalse();
            assertThat(resultado.taxaConcordancia()).isZero();
        }

        @Test
        @DisplayName("20 casos, 16 concordantes por quadrante (80%) — calibrado")
        void vinteCasos16ConcordantesCalibrado() {
            List<EvalJudgeCalibration.ParNotas> pares = new ArrayList<>();
            for (int i = 0; i < 16; i++) {
                pares.add(new EvalJudgeCalibration.ParNotas(5, 4)); // ambos "APROVA"
            }
            for (int i = 0; i < 4; i++) {
                pares.add(new EvalJudgeCalibration.ParNotas(5, 1)); // APROVA vs REJEITA
            }

            var resultado = calibracao.avaliar(pares);

            assertThat(resultado.totalCasos()).isEqualTo(20);
            assertThat(resultado.concordantes()).isEqualTo(16);
            assertThat(resultado.calibrado()).isTrue();
        }

        @Test
        @DisplayName("20 casos, 15 concordantes (75%) — não calibrado")
        void vinteCasos15ConcordantesNaoCalibrado() {
            List<EvalJudgeCalibration.ParNotas> pares = new ArrayList<>();
            for (int i = 0; i < 15; i++) {
                pares.add(new EvalJudgeCalibration.ParNotas(5, 4));
            }
            for (int i = 0; i < 5; i++) {
                pares.add(new EvalJudgeCalibration.ParNotas(5, 1));
            }

            var resultado = calibracao.avaliar(pares);

            assertThat(resultado.calibrado()).isFalse();
        }

        @Test
        @DisplayName("quadrante trata nota 3 (REVISAR) como categoria própria, não concordante com 4 (APROVA)")
        void quadranteRevisarNaoConcordaComAprova() {
            var pares = List.of(new EvalJudgeCalibration.ParNotas(3, 4));

            var resultado = calibracao.avaliar(pares);

            assertThat(resultado.concordantes()).isZero();
        }
    }
}
