package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.output.LapEfficiencySeriesDto;
import br.com.menthoros.backend.entity.EtapaRealizada;
import br.com.menthoros.backend.enums.MotivoOmissaoVolta;
import br.com.menthoros.backend.enums.OrigemCalculo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Série de EF por volta (design D1 de fit-lap-derived-metrics): elegibilidade POR PONTO
 * (série parcial com buracos é aceitável), com metadados de omissão que reconciliam o gráfico
 * com o decoupling escalar.
 */
class LapEfficiencySeriesCalculatorTest {

    private final LapEfficiencySeriesCalculator calculator = new LapEfficiencySeriesCalculator();

    @Nested
    @DisplayName("calcular")
    class Calcular {

        @Test
        @DisplayName("série completa: EF de pace, EF de potência e W/kg por volta, origem POR_VOLTA")
        void serieCompleta() {
            List<EtapaRealizada> etapas = List.of(
                    etapa(1, 150, 12.0, 360),
                    etapa(2, 155, 11.5, 340)
            );

            LapEfficiencySeriesDto serie = calculator.calcular(etapas, BigDecimal.valueOf(80.0));

            assertThat(serie.origem()).isEqualTo(OrigemCalculo.POR_VOLTA);
            assertThat(serie.totalVoltas()).isEqualTo(2);
            assertThat(serie.voltasOmitidas()).isEmpty();
            assertThat(serie.pontos()).hasSize(2);

            var p1 = serie.pontos().get(0);
            assertThat(p1.ordem()).isEqualTo(1);
            assertThat(p1.velocidadeKmh()).isEqualTo(12.0);
            assertThat(p1.fcMedia()).isEqualTo(150);
            assertThat(p1.efPace()).isEqualTo(0.08);       // 12/150
            assertThat(p1.efPotencia()).isEqualTo(2.4);    // 360/150
            assertThat(p1.wPorKg()).isEqualTo(4.5);        // 360/80
        }

        @Test
        @DisplayName("volta sem potência mantém o ponto com efPotencia e wPorKg nulos")
        void semPotenciaMantemPonto() {
            LapEfficiencySeriesDto serie = calculator.calcular(
                    List.of(etapa(1, 150, 12.0, null)), BigDecimal.valueOf(80.0));

            var p = serie.pontos().get(0);
            assertThat(p.efPace()).isEqualTo(0.08);
            assertThat(p.efPotencia()).isNull();
            assertThat(p.wPorKg()).isNull();
        }

        @Test
        @DisplayName("atleta sem peso → wPorKg nulo, efPotencia preservado")
        void semPesoSemWkg() {
            LapEfficiencySeriesDto serie = calculator.calcular(
                    List.of(etapa(1, 150, 12.0, 360)), null);

            var p = serie.pontos().get(0);
            assertThat(p.efPotencia()).isEqualTo(2.4);
            assertThat(p.wPorKg()).isNull();
        }

        @Test
        @DisplayName("voltas inelegíveis viram voltasOmitidas com o motivo certo — série parcial com buracos")
        void voltasOmitidasComMotivo() {
            List<EtapaRealizada> etapas = List.of(
                    etapa(1, 150, 12.0, null),
                    etapa(2, null, 12.0, null),                  // sem FC
                    etapaSemVelocidade(3, 152),                  // sem velocidade/pace
                    etapaDuracaoZero(4, 155, 12.0),              // duração inválida
                    etapa(5, 158, 11.8, null)
            );

            LapEfficiencySeriesDto serie = calculator.calcular(etapas, null);

            assertThat(serie.totalVoltas()).isEqualTo(5);
            assertThat(serie.pontos()).extracting("ordem").containsExactly(1, 5);
            assertThat(serie.voltasOmitidas()).extracting("ordem").containsExactly(2, 3, 4);
            assertThat(serie.voltasOmitidas()).extracting("motivo").containsExactly(
                    MotivoOmissaoVolta.SEM_FC,
                    MotivoOmissaoVolta.SEM_VELOCIDADE,
                    MotivoOmissaoVolta.DURACAO_INVALIDA);
        }

        @Test
        @DisplayName("paceGap permanece nulo com o hard gate desligado, mesmo com elevação disponível")
        void paceGapNuloComGateDesligado() {
            assertThat(GapCalculator.HABILITADO).isFalse();

            EtapaRealizada comElevacao = EtapaRealizada.builder()
                    .ordem(1).duracao(Duration.ofMinutes(6)).fcMedia(150)
                    .velocidadeMedia(BigDecimal.valueOf(10.0))
                    .elevacaoGanhoMetros(30).elevacaoPerdaMetros(0)
                    .distanciaKm(BigDecimal.ONE)
                    .build();

            LapEfficiencySeriesDto serie = calculator.calcular(List.of(comElevacao), null);

            assertThat(serie.pontos().get(0).paceGap()).isNull();
        }

        @Test
        @DisplayName("sem etapas (vazia ou nula) → série nula")
        void semEtapasSerieNula() {
            assertThat(calculator.calcular(List.of(), null)).isNull();
            assertThat(calculator.calcular(null, null)).isNull();
        }

        @Test
        @DisplayName("elemento nulo na lista é ignorado sem NPE")
        void elementoNuloIgnorado() {
            List<EtapaRealizada> etapas = Arrays.asList(etapa(1, 150, 12.0, null), null);

            LapEfficiencySeriesDto serie = calculator.calcular(etapas, null);

            assertThat(serie.totalVoltas()).isEqualTo(1);
            assertThat(serie.pontos()).hasSize(1);
        }
    }

    // ===== fixtures =====

    private static EtapaRealizada etapa(int ordem, Integer fc, Double velKmh, Integer potencia) {
        return EtapaRealizada.builder()
                .ordem(ordem)
                .duracao(Duration.ofMinutes(10))
                .fcMedia(fc)
                .velocidadeMedia(velKmh != null ? BigDecimal.valueOf(velKmh) : null)
                .potenciaMedia(potencia)
                .build();
    }

    private static EtapaRealizada etapaSemVelocidade(int ordem, Integer fc) {
        return EtapaRealizada.builder()
                .ordem(ordem)
                .duracao(Duration.ofMinutes(10))
                .fcMedia(fc)
                .build();
    }

    private static EtapaRealizada etapaDuracaoZero(int ordem, Integer fc, Double velKmh) {
        return EtapaRealizada.builder()
                .ordem(ordem)
                .duracao(Duration.ZERO)
                .fcMedia(fc)
                .velocidadeMedia(BigDecimal.valueOf(velKmh))
                .build();
    }
}
