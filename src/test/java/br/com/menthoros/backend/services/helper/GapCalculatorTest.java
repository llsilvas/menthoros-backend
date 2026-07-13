package br.com.menthoros.backend.services.helper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fórmula v1 do GAP interno (design D2 de fit-lap-derived-metrics). A fórmula é pura e sempre
 * computável — o HARD GATE ({@code GapCalculator.HABILITADO}) é aplicado pelos CONSUMIDORES
 * (série de EF e gate de CV do decoupling), não aqui.
 */
class GapCalculatorTest {

    @Nested
    @DisplayName("custoRelativo")
    class CustoRelativo {

        @Test
        @DisplayName("plano (subida == descida) → custo 1.0")
        void planoCustoNeutro() {
            assertThat(GapCalculator.custoRelativo(5, 5, 1.0)).isEqualTo(1.0);
        }

        @Test
        @DisplayName("subida líquida encarece: g=+3% → custo 1.27")
        void subidaEncarece() {
            assertThat(GapCalculator.custoRelativo(30, 0, 1.0)).isEqualTo(1.27);
        }

        @Test
        @DisplayName("descida líquida barateia menos que a subida encarece: g=-3% → custo 0.865")
        void descidaBarateiaAssimetricamente() {
            assertThat(GapCalculator.custoRelativo(0, 30, 1.0)).isEqualTo(0.865);
        }

        @Test
        @DisplayName("gradiente além de ±10% → null (fora do modelo v1)")
        void gradienteExtremoForaDoModelo() {
            assertThat(GapCalculator.custoRelativo(101, 0, 1.0)).isNull();
            assertThat(GapCalculator.custoRelativo(0, 101, 1.0)).isNull();
            // BVA: exatamente 10% ainda calcula
            assertThat(GapCalculator.custoRelativo(100, 0, 1.0)).isEqualTo(1.9);
        }

        @Test
        @DisplayName("subida+descida acima de 30% da distância → null (barômetro suspeito/trail)")
        void elevacaoDesproporcionalForaDoModelo() {
            // 200m + 150m = 350m > 30% de 1km
            assertThat(GapCalculator.custoRelativo(200, 150, 1.0)).isNull();
            // BVA: exatamente 30% (150+150=300m em 1km) calcula (g=0 → 1.0)
            assertThat(GapCalculator.custoRelativo(150, 150, 1.0)).isEqualTo(1.0);
        }

        @Test
        @DisplayName("sem elevação ou sem distância → null (nunca fabrica)")
        void dadosAusentesRetornamNull() {
            assertThat(GapCalculator.custoRelativo(null, 5, 1.0)).isNull();
            assertThat(GapCalculator.custoRelativo(5, null, 1.0)).isNull();
            assertThat(GapCalculator.custoRelativo(5, 5, null)).isNull();
            assertThat(GapCalculator.custoRelativo(5, 5, 0.0)).isNull();
        }
    }

    @Nested
    @DisplayName("paceGap")
    class PaceGap {

        @Test
        @DisplayName("subida → GAP mais rápido que o pace bruto (CA3)")
        void subidaGapMaisRapido() {
            Duration bruto = Duration.ofMinutes(6); // 6:00/km subindo 3%
            Duration gap = GapCalculator.paceGap(bruto, 30, 0, 1.0);
            // 360s / 1.27 = 283s = 4:43
            assertThat(gap).isEqualTo(Duration.ofSeconds(283)).isLessThan(bruto);
        }

        @Test
        @DisplayName("plano → GAP igual ao pace bruto (CA3)")
        void planoGapIgualAoBruto() {
            Duration bruto = Duration.ofMinutes(6);
            assertThat(GapCalculator.paceGap(bruto, 2, 2, 1.0)).isEqualTo(bruto);
        }

        @Test
        @DisplayName("descida → GAP mais lento que o pace bruto")
        void descidaGapMaisLento() {
            Duration bruto = Duration.ofMinutes(6);
            assertThat(GapCalculator.paceGap(bruto, 0, 30, 1.0)).isGreaterThan(bruto);
        }

        @Test
        @DisplayName("sem sanidade (gradiente extremo) ou sem pace → null")
        void foraDeSanidadeNull() {
            assertThat(GapCalculator.paceGap(Duration.ofMinutes(6), 200, 0, 1.0)).isNull();
            assertThat(GapCalculator.paceGap(null, 5, 5, 1.0)).isNull();
        }
    }

    @Nested
    @DisplayName("velocidadeAjustada")
    class VelocidadeAjustada {

        @Test
        @DisplayName("subida → velocidade equivalente no plano é maior que a bruta")
        void subidaVelocidadeMaior() {
            // 10 km/h subindo 3% ≡ 12.7 km/h no plano
            assertThat(GapCalculator.velocidadeAjustada(10.0, 30, 0, 1.0)).isEqualTo(12.7);
        }

        @Test
        @DisplayName("sem elevação → null")
        void semElevacaoNull() {
            assertThat(GapCalculator.velocidadeAjustada(10.0, null, null, 1.0)).isNull();
        }
    }

    @Nested
    @DisplayName("hard gate")
    class HardGate {

        @Test
        @DisplayName("GAP nasce DESLIGADO — a matriz de calibração está incompleta (design D2)")
        void gapNasceDesligado() {
            // Este teste é o lembrete executável da task 3.4: ligar a flag exige a matriz de
            // fixtures verde no critério duplo, em commit próprio com os números registrados.
            assertThat(GapCalculator.HABILITADO).isFalse();
        }
    }
}
