package br.com.menthoros.backend.skills.race;

import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.skills.race.enums.DataQuality;
import br.com.menthoros.backend.skills.race.enums.ProjectionConfidence;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

/**
 * Camada 1 — Regressão de Pace Normalizado.
 *
 * Filtra sessões TEMPO_RUN e LONGO, normaliza o pace por frequência cardíaca,
 * aplica regressão linear simples (commons-math3) e projeta o pace para a semana da prova.
 *
 * Idempotent: YES — leitura pura, sem side effects.
 * Side Effects: NONE
 * Tenant-aware: NO — opera sobre records imutáveis sem JPA.
 */
@Component
public class PaceRegressionCalculator {

    private static final int MIN_SESSIONS_FOR_REGRESSION = 6;
    private static final double LACTATE_THRESHOLD_HR_FACTOR = 0.88;

    /**
     * Calcula a regressão de pace normalizado e projeta o pace para a semana da prova.
     *
     * @param trainingHistory histórico de treinos com metadados de qualidade
     * @param maxHr           FC máxima do atleta (nullable — ativa fallback de qualidade)
     * @param weeksToRace     semanas até o dia da prova (usadas para extrapolação)
     * @return RegressionResult com pace projetado, R², confiança e metadados de fallback
     */
    public RegressionResult calculate(TrainingHistory trainingHistory, Integer maxHr, int weeksToRace) {
        if (trainingHistory == null) {
            throw new IllegalArgumentException("TrainingHistory cannot be null");
        }

        // SPARSE → fallback obrigatório (tarefa 2.8)
        if (trainingHistory.dataQuality() == DataQuality.SPARSE) {
            return buildSparseFallback(trainingHistory, maxHr);
        }

        List<WorkoutSummary> qualifying = trainingHistory.workouts().stream()
                .filter(w -> w.type() == TipoTreino.TEMPO_RUN || w.type() == TipoTreino.LONGO)
                .filter(w -> w.avgPaceSecPerKm() != null && w.avgPaceSecPerKm() > 0)
                .sorted(Comparator.comparing(WorkoutSummary::date))
                .toList();

        if (qualifying.size() < MIN_SESSIONS_FOR_REGRESSION) {
            return buildInsufficientDataFallback(qualifying, maxHr);
        }

        boolean hrMissing = maxHr == null
                || qualifying.stream().allMatch(w -> w.avgHr() == null);

        String hrAssumption = hrMissing
                ? "Sem dados de FC disponíveis: pace bruto usado como proxy de pace normalizado. Confiança limitada a LOW."
                : null;

        double lactateThrHr = maxHr != null
                ? maxHr * LACTATE_THRESHOLD_HR_FACTOR
                : 0.0; // só usado se hrMissing=false

        LocalDate earliest = qualifying.get(0).date();
        SimpleRegression regression = new SimpleRegression();

        for (WorkoutSummary w : qualifying) {
            double weekOrdinal = ChronoUnit.WEEKS.between(earliest, w.date());
            double normalizedPace = hrMissing
                    ? (double) w.avgPaceSecPerKm()
                    : (double) w.avgPaceSecPerKm() / ((double) w.avgHr() / lactateThrHr);
            regression.addData(weekOrdinal, normalizedPace);
        }

        double currentWeek = ChronoUnit.WEEKS.between(earliest, LocalDate.now());
        double targetWeek = currentWeek + weeksToRace;
        double projectedPace = regression.predict(targetWeek);

        // Garante que o pace projetado seja positivo
        if (projectedPace <= 0) {
            projectedPace = (double) qualifying.get(qualifying.size() - 1).avgPaceSecPerKm();
        }

        double rSquared = regression.getRSquare();
        ProjectionConfidence confidence = hrMissing
                ? ProjectionConfidence.LOW
                : confidenceFromR2(rSquared);

        return new RegressionResult(
                regression.getSlope(),
                rSquared,
                projectedPace,
                confidence,
                qualifying.size(),
                false,
                hrAssumption
        );
    }

    private RegressionResult buildSparseFallback(TrainingHistory history, Integer maxHr) {
        double lastPace = history.workouts().stream()
                .filter(w -> w.avgPaceSecPerKm() != null && w.avgPaceSecPerKm() > 0)
                .max(Comparator.comparing(WorkoutSummary::date))
                .map(w -> (double) w.avgPaceSecPerKm())
                .orElse(360.0); // 6min/km padrão se sem dados

        String hrAssumption = (maxHr == null)
                ? "Sem dados de FC e histórico esparso: pace bruto do último treino usado como estimativa plana."
                : null;

        return new RegressionResult(
                null,
                null,
                lastPace,
                ProjectionConfidence.LOW,
                (int) history.workouts().stream()
                        .filter(w -> w.type() == TipoTreino.TEMPO_RUN || w.type() == TipoTreino.LONGO)
                        .count(),
                true,
                hrAssumption
        );
    }

    private RegressionResult buildInsufficientDataFallback(List<WorkoutSummary> qualifying, Integer maxHr) {
        double lastPace = qualifying.isEmpty()
                ? 360.0
                : (double) qualifying.get(qualifying.size() - 1).avgPaceSecPerKm();

        String hrAssumption = (maxHr == null)
                ? "Sem dados de FC e menos de 6 sessões qualificadas: pace bruto do último treino usado."
                : null;

        return new RegressionResult(
                null,
                null,
                lastPace,
                ProjectionConfidence.LOW,
                qualifying.size(),
                true,
                hrAssumption
        );
    }

    private ProjectionConfidence confidenceFromR2(double rSquared) {
        if (rSquared >= 0.70) return ProjectionConfidence.HIGH;
        if (rSquared >= 0.40) return ProjectionConfidence.MEDIUM;
        return ProjectionConfidence.LOW;
    }
}
