package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.domain.workout.HrTarget;
import br.com.menthoros.backend.dto.llm.v2.Zona;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.FaixaZonaManual;
import br.com.menthoros.backend.enums.ModoZonas;
import br.com.menthoros.backend.services.helper.IntervalsIcuTargetParser.FcAlvoBruto;
import br.com.menthoros.backend.services.helper.ZonaTreinoService.ZonaCompleta;
import br.com.menthoros.backend.services.helper.ZonaTreinoService.ZonaFC;
import br.com.menthoros.backend.services.helper.ZonaTreinoService.ZonaPace;
import br.com.menthoros.backend.services.helper.ZoneResolver.FaixaFc;
import br.com.menthoros.backend.services.helper.ZoneResolver.FaixaPace;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * coach-zonas-manuais, D2: com o eixo em MANUAL, todo consumidor de faixa de zona recebe a faixa
 * gravada; em LIMIAR, o cálculo de sempre. Um teste por consumidor crítico (CA1, CA2, CA4).
 */
@DisplayName("Zonas manuais — precedência em ZonaTreinoService e consumidores")
class ZonasManuaisPrecedenciaTest {

    /** Friel com LTHR 168 dá Z2 = 143–150; a manual tem Z2 = 140–150 e lacuna 150–153. */
    private static final List<FaixaZonaManual> FC_MANUAL = List.of(
            new FaixaZonaManual(1, 120, 140), new FaixaZonaManual(2, 140, 150),
            new FaixaZonaManual(3, 153, 160), new FaixaZonaManual(4, 160, 168),
            new FaixaZonaManual(5, 168, 195));

    private static final List<FaixaZonaManual> PACE_MANUAL = List.of(
            new FaixaZonaManual(1, 350, 380), new FaixaZonaManual(2, 320, 350),
            new FaixaZonaManual(3, 300, 315), new FaixaZonaManual(4, 285, 300),
            new FaixaZonaManual(5, 270, 285), new FaixaZonaManual(6, 240, 270));

    private final ZonaTreinoService service = new ZonaTreinoService();
    private final ZoneResolver zoneResolver = new ZoneResolver(service);
    private final IntervalsIcuFcAlvoResolver push = new IntervalsIcuFcAlvoResolver(service);

    private static Atleta atleta(Integer fcLimiar, Integer fcMaxima, BigDecimal paceLimiar,
                                 boolean fcManual, boolean paceManual) {
        return Atleta.builder()
                .fcLimiar(fcLimiar).fcMaxima(fcMaxima).paceLimiar(paceLimiar)
                .modoZonasFc(fcManual ? ModoZonas.MANUAL : ModoZonas.LIMIAR)
                .zonasManuaisFc(fcManual ? FC_MANUAL : null)
                .modoZonasPace(paceManual ? ModoZonas.MANUAL : ModoZonas.LIMIAR)
                .zonasManuaisPace(paceManual ? PACE_MANUAL : null)
                .build();
    }

    private static BigDecimal min(int segundos) {
        return BigDecimal.valueOf(segundos).divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP);
    }

    @Nested
    @DisplayName("ZonaTreinoService")
    class Servico {

        @Test
        @DisplayName("CA1: FC em MANUAL devolve a faixa gravada, não o Friel")
        void fcManual() {
            List<ZonaFC> zonas = service.zonasFC(atleta(168, 186, null, true, false));

            assertThat(zonas).hasSize(5);
            assertThat(zonas.get(1)).isEqualTo(new ZonaFC(2, "Aeróbico", 140, 150));
            assertThat(zonas.get(4).fcMax()).isEqualTo(195);
        }

        @Test
        @DisplayName("CA2: FC em LIMIAR com faixas antigas na entidade continua no Friel")
        void limiarIgnoraFaixas() {
            Atleta a = atleta(168, 186, null, true, false);
            a.setModoZonasFc(ModoZonas.LIMIAR);

            assertThat(service.zonasFC(a).get(1)).isEqualTo(new ZonaFC(2, "Aeróbico", 143, 150));
        }

        @Test
        @DisplayName("CA4: pace manual e FC pelo limiar coexistem em calcularZonas(Atleta)")
        void eixosIndependentes() {
            List<ZonaCompleta> zonas = service.calcularZonas(atleta(168, 186, BigDecimal.valueOf(5.0), false, true));

            assertThat(zonas.get(1).fc()).isEqualTo(new ZonaFC(2, "Aeróbico", 143, 150));
            assertThat(zonas.get(1).pace()).isEqualTo(new ZonaPace(2, "Aeróbico", min(320), min(350)));
            assertThat(zonas.get(5).pace()).isEqualTo(new ZonaPace(6, "Sprint", min(240), min(270)));
            assertThat(zonas.get(5).fc()).isEqualTo(zonas.get(4).fc());
        }

        @Test
        @DisplayName("pace manual vale mesmo sem paceLimiar")
        void paceManualSemLimiar() {
            List<ZonaPace> zonas = service.zonasPace(atleta(168, 186, null, false, true));

            assertThat(zonas.get(0).paceMax()).isEqualByComparingTo(min(380));
        }

        @Test
        @DisplayName("ida e volta s/km → min/km → s/km devolve o inteiro digitado, de 120 a 900")
        void idaEVolta() {
            for (int seg = 120; seg <= 900; seg++) {
                List<FaixaZonaManual> faixas = List.of(new FaixaZonaManual(1, seg, seg + 1),
                        new FaixaZonaManual(2, 1, 2), new FaixaZonaManual(3, 1, 2),
                        new FaixaZonaManual(4, 1, 2), new FaixaZonaManual(5, 1, 2), new FaixaZonaManual(6, 1, 2));
                Atleta a = Atleta.builder().modoZonasPace(ModoZonas.MANUAL).zonasManuaisPace(faixas).build();

                BigDecimal paceMin = service.zonasPace(a).get(0).paceMin();
                int volta = paceMin.multiply(BigDecimal.valueOf(60)).setScale(0, RoundingMode.HALF_UP).intValue();
                assertThat(volta).as("s/km %d", seg).isEqualTo(seg);
            }
        }

        @ParameterizedTest(name = "FC {0} → Z{1}")
        @CsvSource({"152, 2", "150, 2", "153, 3", "160, 4", "119, 0", "200, 5"})
        @DisplayName("classificação: lacuna fica na zona de baixo, borda na de cima, fora das pontas 0/última")
        void identificarComLacuna(int fc, int zonaEsperada) {
            // Z2 até 150 e Z3 a partir de 153 (lacuna); Z3 até 160 e Z4 a partir de 160 (borda)
            assertThat(service.identificarZonaFC(fc, atleta(168, 186, null, true, false))).isEqualTo(zonaEsperada);
        }

        @Test
        @DisplayName("classificação de pace manual sem paceLimiar não devolve 0")
        void identificarPaceManual() {
            assertThat(service.identificarZonaPace(min(330), atleta(168, 186, null, false, true))).isEqualTo(2);
        }

        @Test
        @DisplayName("zonas de FC 'com dado' (contexto da v1): FC manual sem limiar nem máxima não é null")
        void zonasFcComDado() {
            assertThat(service.calcularZonasFCComDado(atleta(null, null, null, true, false)))
                    .isNotNull().element(1).isEqualTo(new ZonaFC(2, "Aeróbico", 140, 150));
            assertThat(service.calcularZonasFCComDado(atleta(null, null, null, false, false))).isNull();
            assertThat(service.calcularZonasFCComDado(atleta(168, null, null, false, false))).hasSize(5);
        }
    }

    @Nested
    @DisplayName("ZoneResolver (schema v2)")
    class Resolver {

        @Test
        @DisplayName("CA1: bpm(Z2) com FC manual devolve 140–150")
        void bpmManual() {
            AthleteZones zonas = AthleteZones.de(atleta(168, 186, BigDecimal.valueOf(5.0), true, false));

            assertThat(zoneResolver.bpm(Zona.Z2, zonas)).isEqualTo(new FaixaFc(140, 150));
        }

        @Test
        @DisplayName("pace(Z2) com pace manual devolve a faixa manual, mesmo sem limiar")
        void paceManual() {
            AthleteZones zonas = AthleteZones.de(atleta(168, 186, null, false, true));

            assertThat(zoneResolver.pace(Zona.Z2, zonas)).isEqualTo(new FaixaPace(min(320), min(350)));
        }

        @Test
        @DisplayName("LIMIAR continua igual ao cálculo de antes")
        void limiar() {
            AthleteZones zonas = AthleteZones.de(atleta(168, 186, BigDecimal.valueOf(5.0), false, false));

            assertThat(zoneResolver.bpm(Zona.Z2, zonas)).isEqualTo(new FaixaFc(143, 150));
            assertThat(zoneResolver.pace(Zona.Z2, zonas))
                    .isEqualTo(new FaixaPace(new BigDecimal("5.25"), new BigDecimal("5.75")));
        }
    }

    @Nested
    @DisplayName("push intervals.icu")
    class Push {

        @Test
        @DisplayName("CA1: alvo Z2 com FC manual vira 140–150 bpm")
        void zonaManual() {
            var r = push.resolver(new FcAlvoBruto(FcAlvoBruto.Base.ZONE, 2, 2), atleta(168, 200, null, true, false));

            assertThat(r.alvo()).isEqualTo(new HrTarget(140, 150));
        }

        @Test
        @DisplayName("FC manual resolve alvo de zona sem fcLimiar medido")
        void zonaManualSemLimiar() {
            var r = push.resolver(new FcAlvoBruto(FcAlvoBruto.Base.ZONE, 3, 3), atleta(null, 200, null, true, false));

            assertThat(r.alvo()).isEqualTo(new HrTarget(153, 160));
            assertThat(r.descartadoPorFaltaDeDado()).isFalse();
        }

        @Test
        @DisplayName("percentual continua exigindo fcLimiar medido, mesmo com FC manual")
        void percentualSemLimiar() {
            var r = push.resolver(new FcAlvoBruto(FcAlvoBruto.Base.PERCENT, 90, 95), atleta(null, 200, null, true, false));

            assertThat(r.descartadoPorFaltaDeDado()).isTrue();
        }

        @Test
        @DisplayName("Z5 manual acima da FC máxima é descartada, como bpm literal")
        void acimaDaMaxima() {
            var r = push.resolver(new FcAlvoBruto(FcAlvoBruto.Base.ZONE, 5, 5), atleta(168, 188, null, true, false));

            assertThat(r.alvo()).isNull();
            assertThat(r.descartadoPorFaltaDeDado()).isTrue();
        }
    }
}
