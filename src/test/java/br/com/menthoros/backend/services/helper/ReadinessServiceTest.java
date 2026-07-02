package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.config.core.ReadinessProperties;
import br.com.menthoros.backend.entity.CheckinProntidao;
import br.com.menthoros.backend.enums.NivelProntidao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReadinessServiceTest {

    private ReadinessProperties properties;
    private ReadinessService readinessService;

    @BeforeEach
    void setUp() {
        properties = new ReadinessProperties();
        readinessService = new ReadinessService(properties);
    }

    @Nested
    @DisplayName("calcularScore")
    class CalcularScore {

        @Test
        @DisplayName("retorna 1.0 quando todos os sinais são ótimos")
        void retorna1QuandoSinaisOtimos() {
            CheckinProntidao checkin = checkin(10, 10, 0, 10, 0);

            BigDecimal score = readinessService.calcularScore(checkin);

            assertThat(score).isEqualByComparingTo(BigDecimal.ONE);
        }

        @Test
        @DisplayName("retorna 0.0 quando todos os sinais são péssimos")
        void retorna0QuandoSinaisPessimos() {
            CheckinProntidao checkin = checkin(1, 1, 10, 1, 10);

            BigDecimal score = readinessService.calcularScore(checkin);

            assertThat(score).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("soma dos pesos é 1.0 (garantia de score no intervalo 0–1)")
        void somaDosPesosEUm() {
            double soma = properties.getPesoSono() + properties.getPesoEnergia()
                    + properties.getPesoHumor() + properties.getPesoDores()
                    + properties.getPesoEstresse();

            assertThat(soma).isEqualTo(1.0);
        }

        @Test
        @DisplayName("lança IllegalArgumentException quando checkin é null")
        void lancaExcecaoQuandoCheckinNull() {
            assertThatThrownBy(() -> readinessService.calcularScore(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("checkin");
        }

        @Test
        @DisplayName("score fica estritamente entre 0 e 1 em sinais mistos")
        void scoreEntre0e1EmSinaisMistos() {
            CheckinProntidao checkin = checkin(7, 6, 3, 5, 4);

            BigDecimal score = readinessService.calcularScore(checkin);

            assertThat(score.doubleValue()).isBetween(0.0, 1.0);
        }
    }

    @Nested
    @DisplayName("classificarNivel")
    class ClassificarNivel {

        @ParameterizedTest(name = "score={0} -> {1}")
        @DisplayName("classifica corretamente nas 3 faixas, incluindo bordas")
        @CsvSource({
                "1.00, PRONTO",
                "0.75, PRONTO",
                "0.74, CAUTELOSO",
                "0.60, CAUTELOSO",
                "0.50, CAUTELOSO",
                "0.49, DESCANSAR",
                "0.00, DESCANSAR"
        })
        void classificaPorFaixa(String scoreStr, NivelProntidao esperado) {
            BigDecimal score = new BigDecimal(scoreStr);

            NivelProntidao resultado = readinessService.classificarNivel(score);

            assertThat(resultado).isEqualTo(esperado);
        }

        @Test
        @DisplayName("lança IllegalArgumentException quando score é null")
        void lancaExcecaoQuandoScoreNull() {
            assertThatThrownBy(() -> readinessService.classificarNivel(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("score");
        }
    }

    private CheckinProntidao checkin(int sono, int humor, int dores, int energia, int estresse) {
        return CheckinProntidao.builder()
                .qualidadeSono(sono)
                .humor(humor)
                .doresMusculares(dores)
                .nivelEnergia(energia)
                .estresse(estresse)
                .build();
    }
}
