package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.services.helper.ZonaTreinoService.ZonaFC;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ZonaTreinoService — modelo LTHR")
class ZonaTreinoServiceTest {

    private ZonaTreinoService service;

    @BeforeEach
    void setUp() {
        service = new ZonaTreinoService();
    }

    @Nested
    @DisplayName("calcularZonasFC — percentuais LTHR sobre fcLimiar")
    class CalcularZonasFC {

        @Test
        @DisplayName("fcLimiar=160: valores esperados por zona")
        void fcLimiar160_valoresCorretos() {
            List<ZonaFC> zonas = service.calcularZonasFC(190, 160);

            assertThat(zonas).hasSize(5);

            ZonaFC z1 = zonas.get(0);
            assertThat(z1.numero()).isEqualTo(1);
            assertThat(z1.fcMin()).isEqualTo(120); // 75% de 160
            assertThat(z1.fcMax()).isEqualTo(136); // 85% de 160

            ZonaFC z2 = zonas.get(1);
            assertThat(z2.numero()).isEqualTo(2);
            assertThat(z2.fcMin()).isEqualTo(136); // 85% de 160
            assertThat(z2.fcMax()).isEqualTo(142); // 89% de 160

            ZonaFC z3 = zonas.get(2);
            assertThat(z3.numero()).isEqualTo(3);
            assertThat(z3.fcMin()).isEqualTo(142); // 89% de 160
            assertThat(z3.fcMax()).isEqualTo(150); // 94% de 160

            ZonaFC z4 = zonas.get(3);
            assertThat(z4.numero()).isEqualTo(4);
            assertThat(z4.fcMin()).isEqualTo(150); // 94% de 160
            assertThat(z4.fcMax()).isEqualTo(160); // 100% de 160

            ZonaFC z5 = zonas.get(4);
            assertThat(z5.numero()).isEqualTo(5);
            assertThat(z5.fcMin()).isEqualTo(160); // 100% de 160
            assertThat(z5.fcMax()).isEqualTo(170); // 106% de 160
        }

        @Test
        @DisplayName("fcMaxima não afeta o resultado — zonas dependem só do fcLimiar")
        void fcMaximaNaoInfluenciaResultado() {
            List<ZonaFC> comFcMax190 = service.calcularZonasFC(190, 160);
            List<ZonaFC> comFcMax220 = service.calcularZonasFC(220, 160);

            for (int i = 0; i < 5; i++) {
                assertThat(comFcMax190.get(i).fcMin()).isEqualTo(comFcMax220.get(i).fcMin());
                assertThat(comFcMax190.get(i).fcMax()).isEqualTo(comFcMax220.get(i).fcMax());
            }
        }
    }

    @Nested
    @DisplayName("calcularZonas(Atleta) — fallback quando fcLimiar é null")
    class FallbackFcLimiar {

        @Test
        @DisplayName("atleta sem fcLimiar usa getFcLimiarCalculada() — sem NPE")
        void atletaSemFcLimiar_retornaZonasValidas() {
            Atleta atleta = Atleta.builder()
                    .fcMaxima(190)
                    .fcLimiar(null)   // não definido
                    .fcRepouso(50)
                    .build();

            // getFcLimiarCalculada() retorna 85% de FCmax = ~161
            List<ZonaTreinoService.ZonaCompleta> zonas = service.calcularZonas(atleta);

            assertThat(zonas).hasSize(6);
            zonas.forEach(z -> {
                assertThat(z.fc().fcMin()).isGreaterThan(0);
                assertThat(z.fc().fcMax()).isGreaterThanOrEqualTo(z.fc().fcMin());
            });
        }

        @Test
        @DisplayName("atleta sem fcLimiar nem fcMaxima usa fallback de 180 bpm — sem NPE")
        void atletaSemFcLimiarNemFcMax_semNPE() {
            Atleta atleta = Atleta.builder()
                    .fcMaxima(null)
                    .fcLimiar(null)
                    .build();

            // getFcLimiarCalculada() → getFcMaximaCalculada() → fallback 180 → 85% = 153
            List<ZonaTreinoService.ZonaCompleta> zonas = service.calcularZonas(atleta);

            assertThat(zonas).hasSize(6);
        }
    }
}
