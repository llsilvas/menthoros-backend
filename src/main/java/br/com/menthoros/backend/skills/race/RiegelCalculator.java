package br.com.menthoros.backend.skills.race;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Camada 2 — Conversão por fórmula de Riegel.
 *
 * Calibra o expoente usando histórico de provas do atleta (se disponível),
 * seleciona o tempo âncora por ordem de prioridade e projeta tempos para
 * todas as distâncias-alvo.
 *
 * Fórmula: t2 = t1 * (d2 / d1) ^ exponent
 *
 * Idempotent: YES — leitura pura, sem side effects.
 * Side Effects: NONE
 * Tenant-aware: NO — opera sobre records imutáveis sem JPA.
 */
@Component
public class RiegelCalculator {

    static final double DEFAULT_EXPONENT = 1.06;
    private static final int RECENT_RACE_MONTHS = 12;

    /**
     * Calcula os tempos base para cada distância-alvo usando a fórmula de Riegel.
     *
     * @param regression      resultado da Camada 1 (pace projetado e confiança)
     * @param raceHistory     provas anteriores do atleta para calibração do expoente
     * @param targetDistances distâncias-alvo em metros
     * @return RiegelResult com tempos base, expoente usado e metadados de calibração
     */
    public RiegelResult calculate(RegressionResult regression,
                                  List<PastRace> raceHistory,
                                  List<Integer> targetDistances) {
        if (regression == null) throw new IllegalArgumentException("RegressionResult cannot be null");
        if (targetDistances == null || targetDistances.isEmpty()) {
            throw new IllegalArgumentException("targetDistances cannot be null or empty");
        }

        List<PastRace> races = raceHistory != null ? raceHistory : List.of();

        double exponent = DEFAULT_EXPONENT;
        boolean calibrated = false;
        int sampleSize = 0;

        List<PastRace> distinctDistanceRaces = racesWithDistinctDistances(races);
        if (distinctDistanceRaces.size() >= 2) {
            OptionalExponent calibrated_ = calibrateExponent(distinctDistanceRaces);
            if (calibrated_.isPresent()) {
                exponent = calibrated_.value();
                calibrated = true;
                sampleSize = calibrated_.pairCount();
            }
        }

        AnchorSelection anchor = selectAnchor(regression, races, targetDistances);
        Map<Integer, Long> baseTimes = new HashMap<>();
        for (int targetDist : targetDistances) {
            long t2 = Math.round(anchor.timeSeconds() * Math.pow((double) targetDist / anchor.distanceM(), exponent));
            baseTimes.put(targetDist, t2);
        }

        return new RiegelResult(baseTimes, exponent, calibrated, sampleSize, anchor.source());
    }

    // --- exponent calibration ---

    private OptionalExponent calibrateExponent(List<PastRace> races) {
        LocalDate cutoff = LocalDate.now().minusMonths(RECENT_RACE_MONTHS);
        List<PastRace> sorted = races.stream()
                .sorted(Comparator.comparing(PastRace::date))
                .toList();

        double weightedSum = 0.0;
        double totalWeight = 0.0;
        int pairs = 0;

        for (int i = 0; i < sorted.size() - 1; i++) {
            for (int j = i + 1; j < sorted.size(); j++) {
                PastRace r1 = sorted.get(i);
                PastRace r2 = sorted.get(j);
                if (r1.distanceM().equals(r2.distanceM())) continue;

                double d1 = r1.distanceM();
                double d2 = r2.distanceM();
                double t1 = r1.finishTimeSeconds();
                double t2 = r2.finishTimeSeconds();

                double pairExponent = Math.log(t2 / t1) / Math.log(d2 / d1);
                if (!Double.isFinite(pairExponent) || pairExponent <= 0) continue;

                // Peso 2x para provas recentes (< 12 meses)
                double weight = (r2.date().isAfter(cutoff)) ? 2.0 : 1.0;
                weightedSum += pairExponent * weight;
                totalWeight += weight;
                pairs++;
            }
        }

        if (pairs == 0 || totalWeight == 0) return OptionalExponent.empty();
        return OptionalExponent.of(weightedSum / totalWeight, pairs);
    }

    private List<PastRace> racesWithDistinctDistances(List<PastRace> races) {
        Map<Integer, PastRace> bestByDistance = new HashMap<>();
        for (PastRace r : races) {
            bestByDistance.merge(r.distanceM(), r,
                    (existing, candidate) -> candidate.finishTimeSeconds() < existing.finishTimeSeconds()
                            ? candidate : existing);
        }
        return new ArrayList<>(bestByDistance.values());
    }

    // --- anchor selection (priority D9) ---

    private AnchorSelection selectAnchor(RegressionResult regression,
                                         List<PastRace> races,
                                         List<Integer> targetDistances) {
        // Prioridade 1: pace da Camada 1 × distância próxima a uma das target distances
        // Usa a menor distância-alvo como referência para o tempo âncora
        int refDistance = targetDistances.stream().min(Integer::compareTo).orElse(10000);
        double projectedPace = regression.projectedPaceAtRaceWeek();
        long layer1Time = Math.round(projectedPace * (refDistance / 1000.0));
        if (layer1Time > 0) {
            return new AnchorSelection(refDistance, layer1Time, "LAYER1_PACE");
        }

        // Prioridade 2: melhor prova recente (< 12 meses)
        LocalDate cutoff = LocalDate.now().minusMonths(RECENT_RACE_MONTHS);
        Optional<PastRace> bestRecent = races.stream()
                .filter(r -> r.date().isAfter(cutoff))
                .min(Comparator.comparingLong(PastRace::finishTimeSeconds));
        if (bestRecent.isPresent()) {
            PastRace r = bestRecent.get();
            return new AnchorSelection(r.distanceM(), r.finishTimeSeconds(), "BEST_RECENT_RACE");
        }

        // Prioridade 3: estimativa pace TEMPO × 1.05 para 10k
        long tempoEstimate = Math.round(projectedPace * 10 * 1.05);
        return new AnchorSelection(10000, tempoEstimate, "TEMPO_ESTIMATED");
    }

    // --- internal value types ---

    private record AnchorSelection(int distanceM, long timeSeconds, String source) {}

    private static final class OptionalExponent {
        private final double value;
        private final int pairCount;
        private final boolean present;

        private OptionalExponent(double value, int pairCount, boolean present) {
            this.value = value;
            this.pairCount = pairCount;
            this.present = present;
        }

        static OptionalExponent of(double value, int pairCount) {
            return new OptionalExponent(value, pairCount, true);
        }

        static OptionalExponent empty() {
            return new OptionalExponent(0, 0, false);
        }

        boolean isPresent() { return present; }
        double value() { return value; }
        int pairCount() { return pairCount; }
    }
}
