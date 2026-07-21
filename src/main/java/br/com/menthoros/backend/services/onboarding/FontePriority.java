package br.com.menthoros.backend.services.onboarding;

import br.com.menthoros.backend.enums.FonteDados;

import java.util.EnumMap;
import java.util.Map;

/**
 * Ordem de prioridade de fontes (design.md Decisao 2, athlete-onboarding-baseline):
 * Garmin/FIT &gt; Coros/Polar/TrainingPeaks &gt; Strava/intervals.icu &gt; Manual &gt; Declarado.
 * Compartilhado por {@code ActivityDedupService} (desempate de merge) e
 * {@code ConfidenceScorer} (criterio "Fonte confiavel", prioridade 1-2).
 */
public final class FontePriority {

    private static final Map<FonteDados, Integer> PRIORIDADE = new EnumMap<>(FonteDados.class);

    static {
        PRIORIDADE.put(FonteDados.GARMIN, 1);
        PRIORIDADE.put(FonteDados.POLAR, 2);
        PRIORIDADE.put(FonteDados.WAHOO, 2);
        PRIORIDADE.put(FonteDados.TRAINING_PEAKS, 2);
        PRIORIDADE.put(FonteDados.STRAVA, 3);
        PRIORIDADE.put(FonteDados.INTERVALS_ICU, 3);
        PRIORIDADE.put(FonteDados.MANUAL, 4);
        PRIORIDADE.put(FonteDados.IA_GERADO, 5);
    }

    private FontePriority() {
    }

    /** Menor numero = maior prioridade. Fonte desconhecida recebe a menor prioridade possivel. */
    public static int de(FonteDados fonte) {
        return PRIORIDADE.getOrDefault(fonte, 10);
    }
}
