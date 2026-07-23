package br.com.menthoros.backend.services.onboarding;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Utilitario compartilhado entre {@code BaselineCalculator} e
 * {@code ConfidenceScorer} (athlete-onboarding-baseline) — evita duplicar o
 * calculo de "semanas observadas" a partir do historico normalizado.
 */
public final class HistoricoUtils {

    private HistoricoUtils() {
    }

    /** Dias entre a atividade mais antiga do historico e hoje, dividido por 7. */
    public static int semanasObservadas(List<NormalizedActivity> historico) {
        if (historico == null || historico.isEmpty()) {
            return 0;
        }
        LocalDate maisAntiga = historico.stream()
                .map(NormalizedActivity::date)
                .min(LocalDate::compareTo)
                .orElse(LocalDate.now());
        long dias = ChronoUnit.DAYS.between(maisAntiga, LocalDate.now());
        return (int) (dias / 7);
    }
}
