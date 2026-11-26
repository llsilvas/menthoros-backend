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
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
 * por assessoria.
 *
 * <p>Descarga 3:1 e longão (add-season-plan-complete-proposal, T4): a cada 4ª semana de Base+Construção
 * o TSS cai 20% sobre a rampa, sem rebaixá-la; o longão é uma fração do volume, com teto por distância.
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
    /** Sem estado; a curva de redução (T5) é a mesma que o motor semanal usa. */
    private static final TaperStrategy TAPER_STRATEGY = new TaperStrategy();
    /** Abaixo deste CTL o atleta não tem base: na preparação curta, a Base é a última a ser cortada. */
    private static final double CTL_COM_BASE = 40.0;
    private static final double MAX_TSS_RAMP_PER_WEEK = LoadTargetResolver.MAX_CTL_RAMP_PER_WEEK * 7;
    private static final int MIN_WEEKS = 2;
    private static final int DELOAD_EVERY = 4;
    private static final double DELOAD_FACTOR = 0.80;
    private static final BigDecimal LONG_RUN_SHARE = new BigDecimal("0.28");
    private static final BigDecimal MAX_LONG_RUN_SHARE = new BigDecimal("0.50");
    private static final BigDecimal LONG_RUN_DELOAD_FACTOR = new BigDecimal("0.75");
    private static final BigDecimal MAX_LONG_RUN_KM = BigDecimal.valueOf(32);

    private SeasonPlanner() {
    }

    public record WeekPlan(int weekIndex, LocalDate weekStart, TrainingPhase phase,
                           BigDecimal targetVolumeKm, double targetTss, boolean deload,
                           @Nullable BigDecimal longRunKm, @Nullable UUID milestoneRaceId) {
    }

    /** Prova preparatória candidata a marco (T6): data e distância em km. */
    public record RaceMilestone(UUID provaId, LocalDate date, BigDecimal distanceKm) {
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
        return plan(distancia, distanciaKm, dataProva, hoje, startCtl, startVolumeKm, null, List.of());
    }

    /**
     * Como {@link #plan(DistanciaProva, BigDecimal, LocalDate, LocalDate, double, BigDecimal)}, ancorando
     * as provas preparatórias como marcos (T6): uma por semana, a mais longa (empate: a mais cedo), e só
     * as que caem ao menos 7 dias antes da prova-alvo. A descarga que cai num marco passa para a semana
     * seguinte de Base/Construção, ou é pulada.
     *
     * @param startLongRunKm longão de partida (season-plan-start-volume, D2); nulo, o longão é 28% do volume
     */
    public static SeasonPlan plan(DistanciaProva distancia, @Nullable BigDecimal distanciaKm,
                                  LocalDate dataProva, LocalDate hoje,
                                  double startCtl, BigDecimal startVolumeKm, @Nullable BigDecimal startLongRunKm,
                                  List<RaceMilestone> preparatorias) {
        int total = totalWeeks(dataProva, hoje);
        if (total < MIN_WEEKS) {
            throw new IllegalArgumentException("horizonte menor que " + MIN_WEEKS + " semanas: " + total);
        }
        if (startCtl <= 0 || startVolumeKm == null || startVolumeKm.signum() <= 0) {
            throw new IllegalArgumentException("bases de partida precisam ser positivas");
        }

        int minimo = RacePreparationRule.minimoSemanas(distancia, distanciaKm);
        Map<TrainingPhase, Integer> semanas = ajustar(TEMPLATE.get(minimo), total, startCtl);
        return gerar(distancia, distanciaKm, dataProva, hoje, startCtl, startVolumeKm, startLongRunKm, preparatorias,
                semanas, total < minimo);
    }

    /**
     * Como {@link #plan(DistanciaProva, BigDecimal, LocalDate, LocalDate, double, BigDecimal, BigDecimal, List)}, com as
     * semanas por fase escolhidas pelo treinador no recálculo do ajuste (add-season-plan-version-diff), em
     * vez do template: Base, Construção, Pico e Polimento; a semana da prova é sempre uma.
     *
     * @param phaseWeeks semanas de BASE, BUILD, PEAK e TAPER, todas presentes e &ge; 0
     * @throws IllegalArgumentException fase ausente ou negativa, nenhuma semana de subida, ou soma + 1
     *                                  diferente de {@link #totalWeeks}
     */
    public static SeasonPlan planWithPhases(DistanciaProva distancia, @Nullable BigDecimal distanciaKm,
                                            LocalDate dataProva, LocalDate hoje,
                                            double startCtl, BigDecimal startVolumeKm,
                                            @Nullable BigDecimal startLongRunKm,
                                            List<RaceMilestone> preparatorias,
                                            Map<TrainingPhase, Integer> phaseWeeks) {
        int total = totalWeeks(dataProva, hoje);
        if (startCtl <= 0 || startVolumeKm == null || startVolumeKm.signum() <= 0) {
            throw new IllegalArgumentException("bases de partida precisam ser positivas");
        }
        Map<TrainingPhase, Integer> semanas = new EnumMap<>(TrainingPhase.class);
        for (TrainingPhase fase : List.of(BASE, BUILD, PEAK, TAPER)) {
            Integer n = phaseWeeks.get(fase);
            if (n == null || n < 0) {
                throw new IllegalArgumentException("semanas da fase " + fase + " ausentes ou negativas: " + n);
            }
            semanas.put(fase, n);
        }
        semanas.put(RACE_WEEK, 1);
        int soma = semanas.values().stream().mapToInt(Integer::intValue).sum();
        if (soma != total) {
            throw new IllegalArgumentException("as fases somam " + soma + " semanas; o ciclo tem " + total);
        }
        if (semanas.get(BASE) + semanas.get(BUILD) + semanas.get(PEAK) == 0) {
            throw new IllegalArgumentException("a temporada precisa de ao menos uma semana de Base, Construção ou Pico");
        }
        int minimo = RacePreparationRule.minimoSemanas(distancia, distanciaKm);
        return gerar(distancia, distanciaKm, dataProva, hoje, startCtl, startVolumeKm, startLongRunKm, preparatorias,
                semanas, total < minimo);
    }

    private static SeasonPlan gerar(DistanciaProva distancia, @Nullable BigDecimal distanciaKm,
                                    LocalDate dataProva, LocalDate hoje,
                                    double startCtl, BigDecimal startVolumeKm, @Nullable BigDecimal startLongRunKm,
                                    List<RaceMilestone> preparatorias,
                                    Map<TrainingPhase, Integer> semanas, boolean curta) {
        int total = semanas.values().stream().mapToInt(Integer::intValue).sum();
        double inicio = startCtl * 7;
        double pico = inicio * (1 + PEAK_GROWTH);
        int subida = semanas.get(BASE) + semanas.get(BUILD) + semanas.get(PEAK);
        BigDecimal tetoLongao = tetoLongao(distancia, distanciaKm);

        List<WeekPlan> resultado = new ArrayList<>(total);
        LocalDate weekStart = segunda(hoje);
        double referencia = inicio;
        int indice = 0;
        int baseEConstrucao = 0;
        boolean descargaAdiada = false;
        for (TrainingPhase fase : ORDEM) {
            for (int k = 0; k < semanas.get(fase); k++) {
                indice++;
                double tss = switch (fase) {
                    case TAPER, RACE_WEEK -> referencia
                            * (1 - TAPER_STRATEGY.resolverReducaoPercentual(ChronoUnit.DAYS.between(weekStart, dataProva)));
                    default -> inicio + Math.min((pico - inicio) * indice / subida, MAX_TSS_RAMP_PER_WEEK * indice);
                };
                if (indice == subida) {
                    referencia = tss;
                }
                @Nullable UUID marco = marcoDaSemana(preparatorias, weekStart, dataProva);
                boolean subindo = fase == BASE || fase == BUILD;
                if (subindo) {
                    baseEConstrucao++;
                }
                boolean devida = subindo && (baseEConstrucao % DELOAD_EVERY == 0 || descargaAdiada);
                boolean deload = devida && marco == null;
                descargaAdiada = devida && marco != null;
                if (deload) {
                    tss *= DELOAD_FACTOR;
                }
                long tssArredondado = Math.round(tss);
                BigDecimal volume = startVolumeKm.multiply(BigDecimal.valueOf(tssArredondado))
                        .divide(BigDecimal.valueOf(inicio), 1, RoundingMode.HALF_UP);
                resultado.add(new WeekPlan(indice, weekStart, fase, volume, tssArredondado, deload,
                        longao(fase, volume, deload, tetoLongao, startVolumeKm, startLongRunKm), marco));
                weekStart = weekStart.plusWeeks(1);
            }
        }
        return new SeasonPlan(List.copyOf(resultado), curta);
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

    private static @Nullable UUID marcoDaSemana(List<RaceMilestone> preparatorias, LocalDate weekStart,
                                                LocalDate dataProva) {
        LocalDate limite = dataProva.minusDays(7);
        return preparatorias.stream()
                .filter(r -> !r.date().isBefore(weekStart) && r.date().isBefore(weekStart.plusWeeks(1)))
                .filter(r -> !r.date().isAfter(limite))
                .min(Comparator.comparing(RaceMilestone::distanceKm).reversed()
                        .thenComparing(RaceMilestone::date))
                .map(RaceMilestone::provaId)
                .orElse(null);
    }

    /**
     * Longão da semana (season-plan-start-volume, D2): o de partida escalado pelo volume da semana, para
     * seguir rampa, descargas e polimento; nunca abaixo de 28% nem acima de 50% do volume ou do teto da
     * distância.
     */
    private static @Nullable BigDecimal longao(TrainingPhase fase, BigDecimal volume, boolean deload,
                                               BigDecimal teto, BigDecimal startVolumeKm,
                                               @Nullable BigDecimal startLongRunKm) {
        if (fase == RACE_WEEK) {
            return null;
        }
        BigDecimal longao = volume.multiply(LONG_RUN_SHARE);
        if (startLongRunKm != null) {
            longao = longao.max(startLongRunKm.multiply(volume).divide(startVolumeKm, 4, RoundingMode.HALF_UP));
        }
        longao = longao.min(volume.multiply(MAX_LONG_RUN_SHARE)).min(teto);
        if (deload) {
            longao = longao.multiply(LONG_RUN_DELOAD_FACTOR);
        }
        return longao.setScale(1, RoundingMode.HALF_UP);
    }

    /** {@code distanciaKm} informada ou a nominal da categoria; nula para CUSTOMIZADA sem km. */
    public static @Nullable BigDecimal distanciaEfetivaKm(@Nullable DistanciaProva distancia,
                                                          @Nullable BigDecimal distanciaKm) {
        if (distanciaKm != null) {
            return distanciaKm;
        }
        return distancia == null || distancia == DistanciaProva.CUSTOMIZADA
                ? null
                : RacePreparationRule.distanciaNominalKm(distancia);
    }

    private static BigDecimal tetoLongao(DistanciaProva distancia, @Nullable BigDecimal distanciaKm) {
        BigDecimal km = distanciaEfetivaKm(distancia, distanciaKm);
        if (km == null) {
            return MAX_LONG_RUN_KM;
        }
        if (km.compareTo(new BigDecimal("5.5")) <= 0) {
            return BigDecimal.valueOf(12);
        }
        if (km.compareTo(new BigDecimal("10.5")) <= 0) {
            return BigDecimal.valueOf(16);
        }
        if (km.compareTo(new BigDecimal("21.5")) <= 0) {
            return BigDecimal.valueOf(21);
        }
        return MAX_LONG_RUN_KM;
    }

    private static LocalDate segunda(LocalDate data) {
        return data.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}
