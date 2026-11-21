package br.com.menthoros.backend.domain.planner;

import br.com.menthoros.backend.domain.planner.SeasonPlanner.WeekPlan;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static br.com.menthoros.backend.domain.planner.TrainingPhase.BASE;
import static br.com.menthoros.backend.domain.planner.TrainingPhase.BUILD;
import static br.com.menthoros.backend.domain.planner.TrainingPhase.RACE_WEEK;
import static br.com.menthoros.backend.domain.planner.TrainingPhase.TAPER;

/**
 * Validações brandas da temporada (add-season-plan-complete-proposal, T3; épico, Decisão 7): só alertas,
 * nenhum bloqueia a aprovação. Puro e sem Spring; roda sobre as semanas gravadas a cada leitura, então
 * acompanha edições futuras sem persistir nada. Limites como constantes, como o template (D14).
 */
public final class SeasonPlanValidator {

    public enum Rule { RAMP, LONG_RUN_SHARE, DELOAD_CADENCE, TAPER_DEPTH }

    /**
     * {@code measured} e {@code limit} na unidade da regra: %, semanas, pontos percentuais ou, no polimento
     * fora da janela, dias até a prova.
     */
    public record Alert(Rule rule, int weekIndex, double measured, double limit, String message) {
    }

    private static final BigDecimal MAX_RAMP = new BigDecimal("1.10");
    private static final BigDecimal MAX_LONG_RUN_SHARE = new BigDecimal("0.30");
    private static final int MAX_WEEKS_WITHOUT_DELOAD = 4;
    private static final double TAPER_TOLERANCE = 0.10;
    /** Folga de ponto flutuante: igualdade no limite não dispara (T3). */
    private static final double EPSILON = 1e-9;
    private static final TaperStrategy TAPER_STRATEGY = new TaperStrategy();

    private SeasonPlanValidator() {
    }

    /**
     * @param distanciaKm distância efetiva da prova ({@link SeasonPlanner#distanciaEfetivaKm}); nula não avalia
     *                    a janela do polimento.
     */
    public static List<Alert> validate(List<WeekPlan> weeks, LocalDate dataProva, @Nullable BigDecimal distanciaKm) {
        List<Alert> alertas = new ArrayList<>();
        Double pico = null;
        int semDescarga = 0;
        for (int i = 0; i < weeks.size(); i++) {
            WeekPlan w = weeks.get(i);
            if (i > 0) {
                rampa(weeks.get(i - 1), w, alertas);
            }
            longao(w, alertas);

            boolean subindo = w.phase() == BASE || w.phase() == BUILD;
            semDescarga = subindo && !w.deload() ? semDescarga + 1 : 0;
            if (semDescarga > MAX_WEEKS_WITHOUT_DELOAD) {
                alertas.add(new Alert(Rule.DELOAD_CADENCE, w.weekIndex(), semDescarga, MAX_WEEKS_WITHOUT_DELOAD,
                        "%d semanas seguidas sem descarga".formatted(semDescarga)));
                semDescarga = 0;
            }

            boolean taper = w.phase() == TAPER || w.phase() == RACE_WEEK;
            if (w.phase() == TAPER && distanciaKm != null) {
                janela(w, dataProva, distanciaKm, alertas);
            }
            if (!taper && !w.deload()) {
                pico = w.targetTss();
            } else if (taper && pico != null && pico > 0) {
                taper(w, pico, dataProva, alertas);
            }
        }
        return List.copyOf(alertas);
    }

    private static void rampa(WeekPlan anterior, WeekPlan w, List<Alert> alertas) {
        BigDecimal antes = anterior.targetVolumeKm();
        if (anterior.deload() || antes == null || antes.signum() <= 0 || w.targetVolumeKm() == null) {
            return;
        }
        if (w.targetVolumeKm().compareTo(antes.multiply(MAX_RAMP)) > 0) {
            double alta = percentual(w.targetVolumeKm().divide(antes, 4, RoundingMode.HALF_UP).subtract(BigDecimal.ONE));
            double teto = percentual(MAX_RAMP.subtract(BigDecimal.ONE));
            alertas.add(new Alert(Rule.RAMP, w.weekIndex(), alta, teto,
                    "Volume sobe %s%% sobre a semana anterior (teto %s%%)".formatted(fmt(alta), fmt(teto))));
        }
    }

    private static void longao(WeekPlan w, List<Alert> alertas) {
        BigDecimal volume = w.targetVolumeKm();
        if (w.longRunKm() == null || volume == null || volume.signum() <= 0) {
            return;
        }
        if (w.longRunKm().compareTo(volume.multiply(MAX_LONG_RUN_SHARE)) > 0) {
            double fatia = percentual(w.longRunKm().divide(volume, 4, RoundingMode.HALF_UP));
            double teto = percentual(MAX_LONG_RUN_SHARE);
            alertas.add(new Alert(Rule.LONG_RUN_SHARE, w.weekIndex(), fatia, teto,
                    "Longão concentra %s%% do volume da semana (teto %s%%)".formatted(fmt(fatia), fmt(teto))));
        }
    }

    private static void taper(WeekPlan w, double pico, LocalDate dataProva, List<Alert> alertas) {
        double esperada = TAPER_STRATEGY.resolverReducaoPercentual(ChronoUnit.DAYS.between(w.weekStart(), dataProva));
        double real = 1 - w.targetTss() / pico;
        if (Math.abs(real - esperada) > TAPER_TOLERANCE + EPSILON) {
            alertas.add(new Alert(Rule.TAPER_DEPTH, w.weekIndex(), real * 100, esperada * 100,
                    "Redução de %s%% sobre o pico; a curva de polimento pede %s%%"
                            .formatted(fmt(real * 100), fmt(esperada * 100))));
        }
    }

    /** Polimento começando antes da janela da distância (T5): a curva não diz nada sobre ele. */
    private static void janela(WeekPlan w, LocalDate dataProva, BigDecimal distanciaKm, List<Alert> alertas) {
        long dias = ChronoUnit.DAYS.between(w.weekStart(), dataProva);
        long maximo = TAPER_STRATEGY.duracaoMaximaDias(distanciaKm.doubleValue());
        if (dias > maximo) {
            alertas.add(new Alert(Rule.TAPER_DEPTH, w.weekIndex(), dias, maximo,
                    "Polimento começa %d dias antes da prova; a janela da distância é de %d".formatted(dias, maximo)));
        }
    }

    private static double percentual(BigDecimal fracao) {
        return fracao.movePointRight(2).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private static String fmt(double v) {
        return String.format(Locale.forLanguageTag("pt-BR"), "%.1f", v);
    }
}
