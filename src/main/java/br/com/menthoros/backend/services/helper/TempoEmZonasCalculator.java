package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.fit.FitAmostraFc;
import br.com.menthoros.backend.entity.TempoEmZonas;
import br.com.menthoros.backend.services.helper.ZonaTreinoService.ZonaFC;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

/**
 * Tempo em cada zona de FC a partir das amostras {@code record} do .fit (coach-ver-treino-realizado,
 * D6). Função pura: as regras de peso, borda e cobertura estão todas aqui.
 */
public final class TempoEmZonasCalculator {

    /** Pausa do relógio não vira tempo em zona. */
    static final long TETO_INTERVALO_SEG = 5;
    static final double COBERTURA_MINIMA = 0.5;

    private TempoEmZonasCalculator() {}

    /**
     * @param segundosReferencia tempo em movimento da sessão (fallback duração) — base da cobertura mínima
     * @return {@code null} sem amostras com FC, sem zonas ou com cobertura abaixo de 50 %
     */
    public static @Nullable TempoEmZonas calcular(List<FitAmostraFc> amostras, List<ZonaFC> zonas, long segundosReferencia) {
        if (amostras.isEmpty() || zonas.isEmpty()) return null;
        List<ZonaFC> ordenadas = zonas.stream().sorted(Comparator.comparingInt(ZonaFC::numero)).toList();
        int[] segundos = new int[ordenadas.size()];
        long total = 0;

        for (int i = 0; i < amostras.size(); i++) {
            FitAmostraFc amostra = amostras.get(i);
            if (amostra.fc() == null) continue;
            long peso = i + 1 < amostras.size()
                    ? Math.min(amostras.get(i + 1).timestampSeg() - amostra.timestampSeg(), TETO_INTERVALO_SEG)
                    : 1;
            if (peso <= 0) continue;
            segundos[indiceDaZona(amostra.fc(), ordenadas)] += (int) peso;
            total += peso;
        }

        if (total == 0 || total < COBERTURA_MINIMA * segundosReferencia) return null;

        int topo = ordenadas.size() - 1;
        List<TempoEmZonas.Zona> resultado = new java.util.ArrayList<>();
        for (int z = 0; z < ordenadas.size(); z++) {
            ZonaFC zona = ordenadas.get(z);
            // A zona do topo recebe tudo acima dela, então o teto gravado engana a legenda.
            resultado.add(new TempoEmZonas.Zona("Z" + zona.numero(), zona.fcMin(),
                    z == topo ? null : zona.fcMax(), segundos[z]));
        }
        return new TempoEmZonas(TempoEmZonas.FONTE_FIT_RECORDS, resultado);
    }

    /**
     * Primeira zona, de baixo para cima, cujo teto alcança o bpm: na borda compartilhada vence a de
     * baixo. Abaixo do piso dessa zona (abaixo de Z1 ou numa lacuna entre faixas manuais) conta na
     * zona imediatamente abaixo, ou em Z1. Acima de todos os tetos, na zona do topo.
     */
    private static int indiceDaZona(int bpm, List<ZonaFC> zonas) {
        for (int z = 0; z < zonas.size(); z++) {
            if (bpm <= zonas.get(z).fcMax()) {
                return bpm >= zonas.get(z).fcMin() || z == 0 ? z : z - 1;
            }
        }
        return zonas.size() - 1;
    }
}
