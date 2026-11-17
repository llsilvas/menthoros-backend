package br.com.menthoros.backend.domain.planner;

import br.com.menthoros.backend.enums.DistanciaProva;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static br.com.menthoros.backend.domain.planner.TrainingPhase.BASE;
import static br.com.menthoros.backend.domain.planner.TrainingPhase.BUILD;
import static br.com.menthoros.backend.domain.planner.TrainingPhase.PEAK;
import static br.com.menthoros.backend.domain.planner.TrainingPhase.RACE_WEEK;
import static br.com.menthoros.backend.domain.planner.TrainingPhase.TAPER;

/**
 * Motor mínimo do plano de temporada (add-season-plan-generate-tracer, D3/D9/D10): distribui as
 * semanas de segunda a segunda entre as fases do template default e calcula TSS e volume-alvo com
 * uma rampa linear até o pico. Puro e sem Spring; mesma entrada, mesmas semanas.
 *
 * <p>Template default (D9/D14), na duração mínima de {@link RacePreparationRule}: proporções de
 * Pfitzinger para Base/Construção/Pico e Polimento pela janela do {@link TaperStrategy}. Ele vive
 * aqui como constante — shortcut: um único template global; vira tabela quando houver template
 * por assessoria. Descargas, longão e a curva real de taper ficam para o ticket 3.
 */
public final class SeasonPlanner {

    private static final List<TrainingPhase> ORDEM = List.of(BASE, BUILD, PEAK, TAPER, RACE_WEEK);

    /** Semanas por fase (Base, Construção, Pico, Polimento, Semana da prova), por semanas mínimas. */
    private static final Map<Integer, int[]> TEMPLATE = Map.of(
            8, new int[]{3, 2, 2, 0, 1},
            10, new int[]{4, 3, 2, 0, 1},
            12, new int[]{4, 3, 3, 1, 1},
            16, new int[]{6, 4, 3, 2, 1});

    private static final double PEAK_GROWTH = 0.30;
    private static final double TAPER_FACTOR = 0.60;
    private static final double RACE_WEEK_FACTOR = 0.45;
    /** Abaixo deste CTL o atleta não tem base: na preparação curta, a Base é a última a ser cortada. */
    private static final double CTL_COM_BASE = 40.0;
    private static final double MAX_TSS_RAMP_PER_WEEK = LoadTargetResolver.MAX_CTL_RAMP_PER_WEEK * 7;
    private static final int MIN_WEEKS = 2;

    private SeasonPlanner() {
    }

    public record WeekPlan(int weekIndex, LocalDate weekStart, TrainingPhase phase,
                           BigDecimal targetVolumeKm, double targetTss) {
    }

    public record SeasonPlan(List<WeekPlan> weeks, boolean shortPreparation) {
    }

    /** Semanas de segunda a domingo da semana de {@code hoje} até a semana da prova, inclusive; 0 se passada. */
    public static int totalWeeks(LocalDate dataProva, LocalDate hoje) {
        if (dataProva == null || hoje == null) {
            throw new IllegalArgumentException("dataProva e hoje não podem ser nulos");
        }
        if (dataProva.isBefore(hoje)) {
            return 0;
        }
        return (int) ChronoUnit.WEEKS.between(segunda(hoje), segunda(dataProva)) + 1;
    }

    /**
     * Gera as semanas da temporada.
     *
     * @param startCtl      CTL de partida (D11), &gt; 0; o TSS de partida é {@code startCtl × 7}
     * @param startVolumeKm volume semanal de partida (D11), &gt; 0
     * @throws IllegalArgumentException horizonte menor que 2 semanas ou base não positiva
     */
    public static SeasonPlan plan(DistanciaProva distancia, @Nullable BigDecimal distanciaKm,
                                  LocalDate dataProva, LocalDate hoje,
                                  double startCtl, BigDecimal startVolumeKm) {
        int total = totalWeeks(dataProva, hoje);
        if (total < MIN_WEEKS) {
            throw new IllegalArgumentException("horizonte menor que " + MIN_WEEKS + " semanas: " + total);
        }
        if (startCtl <= 0 || startVolumeKm == null || startVolumeKm.signum() <= 0) {
            throw new IllegalArgumentException("bases de partida precisam ser positivas");
        }

        int minimo = RacePreparationRule.minimoSemanas(distancia, distanciaKm);
        Map<TrainingPhase, Integer> semanas = ajustar(TEMPLATE.get(minimo), total, startCtl);

        double inicio = startCtl * 7;
        double pico = inicio * (1 + PEAK_GROWTH);
        int subida = semanas.get(BASE) + semanas.get(BUILD) + semanas.get(PEAK);

        List<WeekPlan> resultado = new ArrayList<>(total);
        LocalDate weekStart = segunda(hoje);
        double referencia = inicio;
        int indice = 0;
        for (TrainingPhase fase : ORDEM) {
            for (int k = 0; k < semanas.get(fase); k++) {
                indice++;
                double tss = switch (fase) {
                    case TAPER -> referencia * TAPER_FACTOR;
                    case RACE_WEEK -> referencia * RACE_WEEK_FACTOR;
                    default -> inicio + Math.min((pico - inicio) * indice / subida, MAX_TSS_RAMP_PER_WEEK * indice);
                };
                if (indice == subida) {
                    referencia = tss;
                }
                long tssArredondado = Math.round(tss);
                BigDecimal volume = startVolumeKm.multiply(BigDecimal.valueOf(tssArredondado))
                        .divide(BigDecimal.valueOf(inicio), 1, RoundingMode.HALF_UP);
                resultado.add(new WeekPlan(indice, weekStart, fase, volume, tssArredondado));
                weekStart = weekStart.plusWeeks(1);
            }
        }
        return new SeasonPlan(List.copyOf(resultado), total < minimo);
    }

    private static Map<TrainingPhase, Integer> ajustar(int[] template, int total, double startCtl) {
        Map<TrainingPhase, Integer> semanas = new EnumMap<>(TrainingPhase.class);
        for (int i = 0; i < ORDEM.size(); i++) {
            semanas.put(ORDEM.get(i), template[i]);
        }
        int diferenca = total - semanas.values().stream().mapToInt(Integer::intValue).sum();
        if (diferenca > 0) {
            semanas.merge(BASE, diferenca, Integer::sum);
            return semanas;
        }
        List<TrainingPhase> ordemDeCorte = startCtl >= CTL_COM_BASE
                ? List.of(BASE, BUILD, PEAK, TAPER)
                : List.of(BUILD, PEAK, BASE, TAPER);
        int aCortar = -diferenca;
        for (TrainingPhase fase : ordemDeCorte) {
            int corte = Math.min(aCortar, semanas.get(fase));
            semanas.put(fase, semanas.get(fase) - corte);
            aCortar -= corte;
        }
        return semanas;
    }

    private static LocalDate segunda(LocalDate data) {
        return data.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}
