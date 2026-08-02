package br.com.menthoros.backend.dto.intervalsicu;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Contrato de desserialização de um intervalo do intervals.icu.
 *
 * <p>Roda contra o payload REAL capturado no gate de contrato da change
 * {@code intervals-icu-activity-laps} (atleta {@code i641775}, activity {@code i171415754}) — não
 * contra um JSON inventado. Foi assim que a change anterior descobriu que a cadência vem de perna
 * única e que {@code average_speed} vem em m/s.
 */
class IcuActivityIntervalDtoTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String FIXTURE = "/fixtures/intervalsicu/activity-com-intervalos.json";

    @Nested
    @DisplayName("desserializacao")
    class Desserializacao {

        @Test
        @DisplayName("le todos os campos do primeiro intervalo do payload real")
        void lePrimeiroIntervalo() throws Exception {
            IcuActivityIntervalDto intervalo = intervalos().get(0);

            assertThat(intervalo.id()).isEqualTo(7130765L);
            assertThat(intervalo.type()).isEqualTo("WORK");
            assertThat(intervalo.label()).isNull();
            assertThat(intervalo.startIndex()).isZero();
            assertThat(intervalo.distance()).isEqualTo(1001.92);
            assertThat(intervalo.movingTimeSeg()).isEqualTo(388);
            assertThat(intervalo.elapsedTimeSeg()).isEqualTo(388);
            assertThat(intervalo.averageSpeed()).isCloseTo(2.582268, within(1e-6));
            assertThat(intervalo.averageHeartrate()).isEqualTo(127.0);
            assertThat(intervalo.maxHeartrate()).isEqualTo(145.0);
            assertThat(intervalo.averageCadence()).isCloseTo(81.3866, within(1e-4));
            assertThat(intervalo.averageWatts()).isNull();
            assertThat(intervalo.totalElevationGain()).isCloseTo(2.4000244, within(1e-6));
        }

        @Test
        @DisplayName("le os running dynamics — presentes e preenchidos no payload real")
        void leRunningDynamics() throws Exception {
            IcuActivityIntervalDto intervalo = intervalos().get(0);

            assertThat(intervalo.averageStride()).isCloseTo(0.95185256, within(1e-6));
            assertThat(intervalo.averageStanceTime()).isCloseTo(249.62077, within(1e-4));
            assertThat(intervalo.averageStanceTimeBalance()).isCloseTo(51.06683, within(1e-4));
            assertThat(intervalo.averageVerticalOscillation()).isCloseTo(113.24149, within(1e-4));
            assertThat(intervalo.averageVerticalRatio()).isCloseTo(11.984201, within(1e-4));
            assertThat(intervalo.averageTemp()).isCloseTo(24.425259, within(1e-4));
        }

        @Test
        @DisplayName("le zona, intensidade e inclinacao — inclinacao vem em FRACAO, nao em %")
        void leZonaIntensidadeInclinacao() throws Exception {
            IcuActivityIntervalDto intervalo = intervalos().get(0);

            assertThat(intervalo.zone()).isEqualTo(1);
            assertThat(intervalo.intensity()).isEqualTo(75.0);
            // 0.0011977126 é 0,1% — o mapper multiplica por 100 (D10).
            assertThat(intervalo.averageGradient()).isCloseTo(0.0011977126, within(1e-9));
        }

        @Test
        @DisplayName("campo desconhecido no JSON nao quebra a desserializacao")
        void ignoraCampoDesconhecido() throws Exception {
            String json = """
                    {"id": 1, "type": "WORK", "distance": 100.0, "campo_que_nao_existe": 42}
                    """;

            IcuActivityIntervalDto intervalo = MAPPER.readValue(json, IcuActivityIntervalDto.class);

            assertThat(intervalo.id()).isEqualTo(1L);
            assertThat(intervalo.type()).isEqualTo("WORK");
        }

        @Test
        @DisplayName("o payload real traz 17 intervalos, um a mais que icu_lap_count")
        void trazDezesseteIntervalos() throws Exception {
            List<IcuActivityIntervalDto> intervalos = intervalos();

            assertThat(intervalos).hasSize(17);
            // O excedente é o intervalo degenerado de 1 s / 2,4 m que o mapper descarta (D4).
            assertThat(intervalos.get(1).movingTimeSeg()).isEqualTo(1);
            assertThat(intervalos.get(1).distance()).isCloseTo(2.4000244, within(1e-6));
        }
    }

    private List<IcuActivityIntervalDto> intervalos() throws Exception {
        try (InputStream in = getClass().getResourceAsStream(FIXTURE)) {
            assertThat(in).as("fixture %s", FIXTURE).isNotNull();
            IcuActivityDto activity = MAPPER.readValue(in, IcuActivityDto.class);
            return activity.intervalos();
        }
    }
}
