package br.com.menthoros.backend.services.onboarding.impl;

import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.services.onboarding.ActivityNormalizer;
import br.com.menthoros.backend.services.onboarding.NormalizedActivity;
import br.com.menthoros.backend.services.onboarding.Sport;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

/**
 * Implementacao do Activity Normalizer (design.md Decisao 1, athlete-onboarding-baseline).
 */
@Component
public class ActivityNormalizerImpl implements ActivityNormalizer {

    /**
     * Confiabilidade por fonte, mesma ordem de prioridade de dedup ja definida
     * em design.md Decisao 2 (Garmin/FIT &gt; Coros/Polar/TrainingPeaks &gt; Strava &gt;
     * Planilha &gt; Manual &gt; Declarado).
     */
    private static final Map<FonteDados, Double> CONFIABILIDADE_FONTE = new EnumMap<>(FonteDados.class);

    static {
        CONFIABILIDADE_FONTE.put(FonteDados.GARMIN, 1.0);
        CONFIABILIDADE_FONTE.put(FonteDados.POLAR, 0.9);
        CONFIABILIDADE_FONTE.put(FonteDados.WAHOO, 0.9);
        CONFIABILIDADE_FONTE.put(FonteDados.TRAINING_PEAKS, 0.9);
        CONFIABILIDADE_FONTE.put(FonteDados.STRAVA, 0.8);
        CONFIABILIDADE_FONTE.put(FonteDados.INTERVALS_ICU, 0.8);
        CONFIABILIDADE_FONTE.put(FonteDados.MANUAL, 0.4);
        CONFIABILIDADE_FONTE.put(FonteDados.IA_GERADO, 0.3);
    }

    @Override
    public NormalizedActivity toCanonical(TreinoRealizado treino) {
        if (treino == null) {
            throw new IllegalArgumentException("treino nao pode ser nulo");
        }

        Double distanceKm = arredondar2Casas(treino.getDistanciaKm());
        Integer durationMinutes = treino.getDuracaoMin() != null
                ? (int) treino.getDuracaoMin().toMinutes()
                : null;

        double completude = calcularCompletude(treino);
        double confiabilidadeFonte = CONFIABILIDADE_FONTE.getOrDefault(treino.getFonteDados(), 0.3);
        double consistenciaInterna = calcularConsistenciaInterna(treino, distanceKm, durationMinutes);
        double dataQuality = 0.5 * completude + 0.3 * confiabilidadeFonte + 0.2 * consistenciaInterna;

        return new NormalizedActivity(
                treino.getId(),
                treino.getExternalId(),
                treino.getAtleta() != null ? treino.getAtleta().getId() : null,
                treino.getDataTreino(),
                Sport.RUNNING, // ja filtrado na ingestao de cada conector (Run/TrailRun/VirtualRun/Treadmill)
                durationMinutes,
                distanceKm,
                treino.getFcMedia(),
                treino.getFcMax(),
                treino.getPaceMedia(), // ja em min:seg/km (Duration) — nao precisa converter
                treino.getPotenciaMedia(), // null se ausente — nunca 0
                treino.getPercepcaoEsforco(), // null se ausente — nunca estimado de FC
                treino.getFonteDados(),
                dataQuality
        );
    }

    private Double arredondar2Casas(BigDecimal valor) {
        if (valor == null) {
            return null;
        }
        return valor.setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private double calcularCompletude(TreinoRealizado treino) {
        int camposEsperados = 5; // fcMedia, paceMedia, potenciaMedia, percepcaoEsforco, distanciaKm
        int camposPresentes = 0;
        if (treino.getFcMedia() != null) camposPresentes++;
        if (treino.getPaceMedia() != null) camposPresentes++;
        if (treino.getPotenciaMedia() != null) camposPresentes++;
        if (treino.getPercepcaoEsforco() != null) camposPresentes++;
        if (treino.getDistanciaKm() != null) camposPresentes++;
        return (double) camposPresentes / camposEsperados;
    }

    private double calcularConsistenciaInterna(TreinoRealizado treino, Double distanceKm, Integer durationMinutes) {
        Duration pace = treino.getPaceMedia();
        if (pace == null || distanceKm == null || durationMinutes == null || distanceKm <= 0) {
            return 0.5; // sem dado suficiente para avaliar consistencia — neutro, nao penaliza nem premia
        }
        double duracaoEsperadaMin = (pace.toSeconds() / 60.0) * distanceKm;
        double desvioRelativo = Math.abs(duracaoEsperadaMin - durationMinutes) / Math.max(durationMinutes, 1);
        if (desvioRelativo <= 0.10) {
            return 1.0;
        }
        if (desvioRelativo <= 0.25) {
            return 0.7;
        }
        return 0.3;
    }
}
