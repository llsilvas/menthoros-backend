package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.entity.TempoEmZonas;
import br.com.menthoros.backend.dto.fit.FitAmostraFc;
import br.com.menthoros.backend.services.helper.ZonaTreinoService.ZonaFC;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Regras do D6 de coach-ver-treino-realizado, uma por teste. */
class TempoEmZonasCalculatorTest {

    /** Faixas contíguas com borda compartilhada (máx de uma = mín da seguinte). */
    private static final List<ZonaFC> ZONAS = List.of(
            new ZonaFC(1, "Z1", 100, 120),
            new ZonaFC(2, "Z2", 120, 140),
            new ZonaFC(3, "Z3", 140, 160),
            new ZonaFC(4, "Z4", 160, 180),
            new ZonaFC(5, "Z5", 180, 190));

    private static List<FitAmostraFc> serie(int segundos, int fc) {
        List<FitAmostraFc> amostras = new ArrayList<>();
        for (int i = 0; i < segundos; i++) amostras.add(new FitAmostraFc(1_000L + i, fc));
        return amostras;
    }

    private static int segundos(TempoEmZonas resultado, String zona) {
        return resultado.zonas().stream().filter(z -> z.zona().equals(zona)).findFirst().orElseThrow().segundos();
    }

    @Test
    @DisplayName("série regular de 1 s: cada amostra vale 1 s, última inclusa")
    void serieRegular() {
        TempoEmZonas r = TempoEmZonasCalculator.calcular(serie(60, 130), ZONAS, 60);

        assertThat(r.fonte()).isEqualTo("FIT_RECORDS");
        assertThat(segundos(r, "Z2")).isEqualTo(60);
        assertThat(r.zonas()).extracting(TempoEmZonas.Zona::segundos).containsExactly(0, 60, 0, 0, 0);
    }

    @Test
    @DisplayName("grava as cinco zonas com os limites usados; a zona do topo fica sem teto")
    void gravaLimites() {
        TempoEmZonas r = TempoEmZonasCalculator.calcular(serie(10, 130), ZONAS, 10);

        assertThat(r.zonas()).containsExactly(
                new TempoEmZonas.Zona("Z1", 100, 120, 0),
                new TempoEmZonas.Zona("Z2", 120, 140, 10),
                new TempoEmZonas.Zona("Z3", 140, 160, 0),
                new TempoEmZonas.Zona("Z4", 160, 180, 0),
                new TempoEmZonas.Zona("Z5", 180, null, 0));
    }

    @Test
    @DisplayName("pausa maior que 5 s conta no máximo 5 s")
    void pausaTemTeto() {
        List<FitAmostraFc> amostras = List.of(new FitAmostraFc(0, 130), new FitAmostraFc(247, 150), new FitAmostraFc(248, 150));

        TempoEmZonas r = TempoEmZonasCalculator.calcular(amostras, ZONAS, 7);

        assertThat(segundos(r, "Z2")).isEqualTo(5);
        assertThat(segundos(r, "Z3")).isEqualTo(2);
    }

    @Test
    @DisplayName("timestamp repetido ou invertido: a amostra não soma tempo")
    void timestampRepetidoOuInvertido() {
        List<FitAmostraFc> amostras = List.of(
                new FitAmostraFc(10, 130), new FitAmostraFc(10, 150), // repetido: o primeiro não soma
                new FitAmostraFc(11, 150), new FitAmostraFc(9, 170),  // invertido: o 150 de t=11 não soma
                new FitAmostraFc(12, 170));

        TempoEmZonas r = TempoEmZonasCalculator.calcular(amostras, ZONAS, 4);

        assertThat(segundos(r, "Z2")).isZero();
        assertThat(segundos(r, "Z3")).isEqualTo(1);
        assertThat(segundos(r, "Z4")).isEqualTo(4); // t=9→12 vale 3; t=12 é a última, 1 s
    }

    @Test
    @DisplayName("amostra sem FC não soma, e o intervalo dela não vai para ninguém")
    void amostraSemFc() {
        List<FitAmostraFc> amostras = List.of(new FitAmostraFc(0, 130), new FitAmostraFc(1, null), new FitAmostraFc(2, 130));

        TempoEmZonas r = TempoEmZonasCalculator.calcular(amostras, ZONAS, 3);

        assertThat(segundos(r, "Z2")).isEqualTo(2);
    }

    @Test
    @DisplayName("bpm na borda compartilhada conta na zona de baixo")
    void bordaCompartilhada() {
        TempoEmZonas r = TempoEmZonasCalculator.calcular(serie(10, 140), ZONAS, 10);

        assertThat(segundos(r, "Z2")).isEqualTo(10);
        assertThat(segundos(r, "Z3")).isZero();
    }

    @Test
    @DisplayName("bpm numa lacuna entre faixas manuais vai para a zona imediatamente abaixo")
    void lacunaEntreFaixas() {
        List<ZonaFC> comLacuna = List.of(
                new ZonaFC(1, "Z1", 100, 119), new ZonaFC(2, "Z2", 125, 139), new ZonaFC(3, "Z3", 140, 159),
                new ZonaFC(4, "Z4", 160, 179), new ZonaFC(5, "Z5", 180, 190));

        TempoEmZonas r = TempoEmZonasCalculator.calcular(serie(10, 122), comLacuna, 10);

        assertThat(segundos(r, "Z1")).isEqualTo(10);
    }

    @Test
    @DisplayName("FC abaixo de Z1 conta em Z1; acima do topo conta em Z5")
    void foraDasFaixas() {
        List<FitAmostraFc> amostras = new ArrayList<>(serie(5, 80));
        for (int i = 0; i < 5; i++) amostras.add(new FitAmostraFc(1_005L + i, 210));

        TempoEmZonas r = TempoEmZonasCalculator.calcular(amostras, ZONAS, 10);

        assertThat(segundos(r, "Z1")).isEqualTo(5);
        assertThat(segundos(r, "Z5")).isEqualTo(5);
    }

    @Test
    @DisplayName("cobertura abaixo de 50 % do tempo de referência → nulo")
    void coberturaBaixa() {
        assertThat(TempoEmZonasCalculator.calcular(serie(49, 130), ZONAS, 100)).isNull();
        assertThat(TempoEmZonasCalculator.calcular(serie(50, 130), ZONAS, 100)).isNotNull();
    }

    @Test
    @DisplayName("sem amostras, sem zonas ou sem FC nenhuma → nulo")
    void semDados() {
        assertThat(TempoEmZonasCalculator.calcular(List.of(), ZONAS, 100)).isNull();
        assertThat(TempoEmZonasCalculator.calcular(serie(10, 130), List.of(), 10)).isNull();
        assertThat(TempoEmZonasCalculator.calcular(List.of(new FitAmostraFc(0, null)), ZONAS, 1)).isNull();
    }

    @Test
    @DisplayName("soma das zonas = soma dos pesos (série com pausas)")
    void somaBateComPesos() {
        List<FitAmostraFc> amostras = new ArrayList<>(serie(100, 130));
        amostras.addAll(List.of(new FitAmostraFc(1_200, 150), new FitAmostraFc(1_201, 165)));

        TempoEmZonas r = TempoEmZonasCalculator.calcular(amostras, ZONAS, 102);

        // 99 × 1 s + 5 s (pausa de 101 s com teto) + 1 s + última 1 s
        assertThat(r.zonas().stream().mapToInt(TempoEmZonas.Zona::segundos).sum()).isEqualTo(106);
    }
}
