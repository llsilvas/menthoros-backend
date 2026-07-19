package br.com.menthoros.backend.domain.planner;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class TaperStrategyTest {

    private final TaperStrategy taper = new TaperStrategy();

    @Nested
    @DisplayName("estaNaJanelaDeTaper — duracao por distancia")
    class DuracaoPorDistancia {

        @Test
        @DisplayName("5-10K: janela de ate 7 dias")
        void distanciaCurtaJanelaDe7Dias() {
            assertThat(taper.estaNaJanelaDeTaper(10.0, 7)).isTrue();
            assertThat(taper.estaNaJanelaDeTaper(10.0, 8)).isFalse();
        }

        @Test
        @DisplayName("21K: janela de ate 14 dias")
        void meiaMaratonaJanelaDe14Dias() {
            assertThat(taper.estaNaJanelaDeTaper(21.0975, 14)).isTrue();
            assertThat(taper.estaNaJanelaDeTaper(21.0975, 15)).isFalse();
        }

        @Test
        @DisplayName("42K/Ironman: janela de ate 21 dias")
        void maratonaJanelaDe21Dias() {
            assertThat(taper.estaNaJanelaDeTaper(42.195, 21)).isTrue();
            assertThat(taper.estaNaJanelaDeTaper(42.195, 22)).isFalse();
        }

        @Test
        @DisplayName("fora da janela nunca fica negativo")
        void diasNegativosForaDaJanela() {
            assertThat(taper.estaNaJanelaDeTaper(21.0975, -1)).isFalse();
        }
    }

    @Nested
    @DisplayName("resolverReducaoPercentual — CA5, curva de reducao")
    class CurvaDeReducao {

        @Test
        @DisplayName("prova 21K a 10 dias reduz TSS-alvo entre 40% e 60% do pico pre-taper")
        void prova21kA10DiasReduz40a60Porcento() {
            double reducao = taper.resolverReducaoPercentual(10);

            assertThat(reducao).isBetween(0.40, 0.60);
        }

        @Test
        @DisplayName("reducao cresce conforme a prova se aproxima (monotonicamente decrescente em dias)")
        void reducaoCresceConformeAproximaDaProva() {
            double reducao14dias = taper.resolverReducaoPercentual(14);
            double reducao10dias = taper.resolverReducaoPercentual(10);
            double reducao5dias = taper.resolverReducaoPercentual(5);

            assertThat(reducao10dias).isGreaterThan(reducao14dias);
            assertThat(reducao5dias).isGreaterThan(reducao10dias);
        }

        @Test
        @DisplayName("RACE_WEEK (semana da prova) e o ponto terminal da curva — reducao proxima do maximo")
        void raceWeekEhTerminalDaCurva() {
            double reducaoRaceWeek = taper.resolverReducaoPercentual(2);

            assertThat(reducaoRaceWeek).isGreaterThanOrEqualTo(0.55);
        }

        @Test
        @DisplayName("aplicar reduz o WeeklyLoadTarget preservando a faixa min/max proporcional")
        void aplicarReduzWeeklyLoadTarget() {
            WeeklyLoadTarget picoPreTaper = new WeeklyLoadTarget(400.0, 360.0, 440.0, "pico pre-taper");

            WeeklyLoadTarget alvoComTaper = taper.aplicar(picoPreTaper, 10);

            double reducaoEsperada = taper.resolverReducaoPercentual(10);
            assertThat(alvoComTaper.targetTss()).isCloseTo(400.0 * (1 - reducaoEsperada), offset(0.01));
            assertThat(alvoComTaper.targetTss()).isLessThan(picoPreTaper.targetTss());
        }
    }

    @Nested
    @DisplayName("preservarIntensidade — zonas de intensidade preservadas")
    class PreservarIntensidade {

        @Test
        @DisplayName("reduz TSS por sessao proporcionalmente, mantendo dia, tipo e zona de intensidade")
        void reduzTssMantendoZonas() {
            List<SessionSlot> preTaper = List.of(
                    new SessionSlot(DayOfWeek.TUESDAY, "INTERVALADO", 80.0, "Z4", false),
                    new SessionSlot(DayOfWeek.SATURDAY, "LONGO", 120.0, "Z2", true)
            );

            List<SessionSlot> comTaper = taper.preservarIntensidade(preTaper, 0.40);

            assertThat(comTaper).hasSize(2);
            assertThat(comTaper.get(0).day()).isEqualTo(DayOfWeek.TUESDAY);
            assertThat(comTaper.get(0).sessionType()).isEqualTo("INTERVALADO");
            assertThat(comTaper.get(0).intensityZone()).isEqualTo("Z4");
            assertThat(comTaper.get(0).targetTss()).isCloseTo(48.0, offset(0.01)); // 80 * (1-0.40)

            assertThat(comTaper.get(1).intensityZone()).isEqualTo("Z2");
            assertThat(comTaper.get(1).chave()).isTrue();
            assertThat(comTaper.get(1).targetTss()).isCloseTo(72.0, offset(0.01)); // 120 * (1-0.40)
        }
    }
}
