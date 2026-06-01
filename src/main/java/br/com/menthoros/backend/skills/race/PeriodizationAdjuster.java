package br.com.menthoros.backend.skills.race;

import br.com.menthoros.backend.skills.race.enums.PeriodizationPhase;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Camada 3 — Ajuste por Periodização e TSB projetado.
 *
 * Aplica um fator multiplicativo sobre os tempos base da Camada 2 com base
 * na fase de periodização e no TSB projetado para o dia da prova.
 *
 * Regra de precedência: FATIGUED (TSB < -15) tem prioridade sobre a fase informada.
 * Regra de OVERTAPERED: TSB > +15 aplica penalidade de destreino independente da fase.
 *
 * Idempotent: YES — leitura pura, sem side effects.
 * Side Effects: NONE
 * Tenant-aware: NO — opera sobre records imutáveis sem JPA.
 */
@Component
public class PeriodizationAdjuster {

    // Limites de TSB que disparam precedência independente da fase
    private static final double TSB_FATIGUED_THRESHOLD = -15.0;
    private static final double TSB_OVERTAPERED_THRESHOLD = 15.0;

    /**
     * Ajusta os tempos base aplicando o fator da fase/TSB.
     *
     * @param baseTimes mapa distância(m) → tempo base em segundos (saída da Camada 2)
     * @param phase     fase de periodização planejada informada no LoadProjection
     * @param projectedTsb TSB projetado para o dia da prova
     * @return AdjustmentResult com fator, rationale e tempos ajustados
     */
    public AdjustmentResult adjust(Map<Integer, Long> baseTimes,
                                   PeriodizationPhase phase,
                                   Double projectedTsb) {
        if (baseTimes == null || baseTimes.isEmpty()) {
            throw new IllegalArgumentException("baseTimes cannot be null or empty");
        }
        if (phase == null) {
            throw new IllegalArgumentException("PeriodizationPhase cannot be null");
        }

        PeriodizationPhase effectivePhase = resolveEffectivePhase(phase, projectedTsb);
        double factor = factorFor(effectivePhase);

        Map<Integer, Long> adjusted = new HashMap<>();
        for (Map.Entry<Integer, Long> entry : baseTimes.entrySet()) {
            adjusted.put(entry.getKey(), Math.round(entry.getValue() * factor));
        }

        return new AdjustmentResult(factor, effectivePhase.name(), adjusted);
    }

    /**
     * Resolve a fase efetiva considerando precedências de TSB.
     * FATIGUED e OVERTAPERED têm precedência sobre a fase informada.
     */
    private PeriodizationPhase resolveEffectivePhase(PeriodizationPhase declared, Double tsb) {
        if (tsb == null) return declared;
        if (tsb < TSB_FATIGUED_THRESHOLD) return PeriodizationPhase.FATIGUED;
        if (tsb > TSB_OVERTAPERED_THRESHOLD) return PeriodizationPhase.OVERTAPERED;
        return declared;
    }

    /**
     * Tabela de fatores por fase (tarefas 4.2 do tasks.md).
     *
     * TAPER_OPTIMAL     tsb -5..+10  → 0.975  (ganho de 2.5%)
     * BUILD_PEAK        tsb -15..-5  → 1.000  (neutro)
     * ESPECIFICO_FRESH  tsb -5..+5   → 0.985
     * BASE_CONSERVATIVE BASE         → 1.050
     * FATIGUED          tsb < -15    → 1.080  (penalidade 8%)
     * OVERTAPERED       tsb > +15    → 1.030  (perda de forma por destreino)
     */
    private double factorFor(PeriodizationPhase phase) {
        return switch (phase) {
            case TAPER_OPTIMAL    -> 0.975;
            case BUILD_PEAK       -> 1.000;
            case ESPECIFICO_FRESH -> 0.985;
            case BASE_CONSERVATIVE -> 1.050;
            case FATIGUED         -> 1.080;
            case OVERTAPERED      -> 1.030;
        };
    }
}
