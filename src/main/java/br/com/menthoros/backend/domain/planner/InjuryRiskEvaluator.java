package br.com.menthoros.backend.domain.planner;

import br.com.menthoros.backend.dto.ProgressaoHistoricoResumo;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Risco fisiologico primario via TSB de {@code ProgressaoHistoricoResumo} — sem ACWR nem
 * {@code AthleteLoadHistory} (design.md Decisao 15, CA3). Monotonia (Foster) e sinal
 * secundario: janela de 7 dias, {@code media/desvio-padrao > 2.0} eleva o nivel de risco em
 * pelo menos um degrau (nunca rebaixa).
 */
public class InjuryRiskEvaluator {

    private static final double TSB_SAFE_THRESHOLD = -10.0;
    private static final double TSB_HIGH_RISK_THRESHOLD = -30.0;
    private static final double MONOTONY_WARNING_THRESHOLD = 2.0;
    private static final int MONOTONY_WINDOW_DIAS = 7;

    public InjuryRiskAssessment assess(ProgressaoHistoricoResumo historico,
                                        List<TreinoRealizadoSnapshot> historicoDiario,
                                        LocalDate referenceDate) {
        double tsb = historico.tsbAtual() != null ? historico.tsbAtual() : 0.0;

        InjuryRiskLevel level;
        boolean requiresCoachReview;
        String reason;

        if (tsb < TSB_HIGH_RISK_THRESHOLD) {
            level = InjuryRiskLevel.HIGH_RISK;
            requiresCoachReview = true;
            reason = "TSB abaixo de -30 (fadiga acumulada alta)";
        } else if (tsb < TSB_SAFE_THRESHOLD) {
            level = InjuryRiskLevel.WARNING;
            requiresCoachReview = false;
            reason = "TSB entre -30 e -10 (fadiga moderada)";
        } else {
            level = InjuryRiskLevel.SAFE;
            requiresCoachReview = false;
            reason = null;
        }

        double monotonia = calcularMonotonia(historicoDiario, referenceDate);
        if (monotonia > MONOTONY_WARNING_THRESHOLD && level == InjuryRiskLevel.SAFE) {
            level = InjuryRiskLevel.WARNING;
            reason = "Monotonia de treino elevada (>2.0) na ultima semana";
        }

        return new InjuryRiskAssessment(level, requiresCoachReview, reason);
    }

    private double calcularMonotonia(List<TreinoRealizadoSnapshot> historicoDiario, LocalDate referenceDate) {
        LocalDate inicioJanela = referenceDate.minusDays(MONOTONY_WINDOW_DIAS);

        Map<LocalDate, Integer> tssPorDia = historicoDiario.stream()
                .filter(t -> !t.dataTreino().isBefore(inicioJanela) && t.dataTreino().isBefore(referenceDate))
                .collect(Collectors.groupingBy(
                        TreinoRealizadoSnapshot::dataTreino,
                        Collectors.summingInt(t -> t.tssCalculado() != null ? t.tssCalculado() : 0)));

        double[] valoresDiarios = new double[MONOTONY_WINDOW_DIAS];
        for (int i = 0; i < MONOTONY_WINDOW_DIAS; i++) {
            LocalDate dia = inicioJanela.plusDays(i);
            valoresDiarios[i] = tssPorDia.getOrDefault(dia, 0);
        }

        double media = 0.0;
        for (double v : valoresDiarios) {
            media += v;
        }
        media /= MONOTONY_WINDOW_DIAS;

        if (media == 0.0) {
            return 0.0; // repouso total na janela; sem sinal de monotonia
        }

        double somaQuadrados = 0.0;
        for (double v : valoresDiarios) {
            somaQuadrados += Math.pow(v - media, 2);
        }
        double desvioPadrao = Math.sqrt(somaQuadrados / MONOTONY_WINDOW_DIAS);

        if (desvioPadrao == 0.0) {
            return Double.MAX_VALUE; // carga identica todos os dias — monotonia maxima
        }

        return media / desvioPadrao;
    }
}
