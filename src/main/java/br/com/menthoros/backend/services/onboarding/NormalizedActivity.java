package br.com.menthoros.backend.services.onboarding;

import br.com.menthoros.backend.enums.FonteDados;

import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Estrutura canonica de uma atividade importada, apos o Activity Normalizer
 * (design.md Decisao 1, athlete-onboarding-baseline). Toda atividade de
 * qualquer fonte (Garmin, Strava, intervals.icu, manual, planilha) e
 * convertida para este formato antes do Baseline Calculator / Confidence
 * Scorer consumirem.
 */
public record NormalizedActivity(
        UUID treinoRealizadoId,
        String activityId,
        UUID athleteId,
        LocalDate date,
        Sport sport,
        Integer durationMinutes,
        Double distanceKm,
        Integer averageHeartRate,
        Integer maxHeartRate,
        Duration averagePace,
        Integer averagePower,
        Integer rpe,
        FonteDados source,
        double dataQuality
) {
}
