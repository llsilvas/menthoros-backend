package br.com.menthoros.backend.services.onboarding.impl;

import br.com.menthoros.backend.services.onboarding.ConfidenceScoreResult;
import br.com.menthoros.backend.services.onboarding.ConfidenceScorer;
import br.com.menthoros.backend.services.onboarding.ConfidenceScorerInput;
import br.com.menthoros.backend.services.onboarding.ConfidenceTier;
import br.com.menthoros.backend.services.onboarding.FontePriority;
import br.com.menthoros.backend.services.onboarding.HistoricoUtils;
import br.com.menthoros.backend.services.onboarding.NormalizedActivity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Implementacao do Confidence Scorer (design.md Decisao 3, athlete-onboarding-baseline).
 * 8 criterios ponderados somando 100 pontos; tier derivado do score, com
 * bonus coach-como-proxy aplicado por ultimo (sobe um tier, nunca desce).
 */
@Slf4j
@Component
public class ConfidenceScorerImpl implements ConfidenceScorer {

    private static final int SEMANAS_TETO_HISTORICO = 8;
    private static final double LIMIAR_FC_PROPORCAO_ATIVIDADES = 0.70;
    private static final int PRIORIDADE_FONTE_CONFIAVEL_MAXIMA = 2; // Garmin=1, Polar/Wahoo/TrainingPeaks=2

    private static final int PESO_HISTORICO = 20;
    private static final int PESO_ONBOARDING_COMPLETO = 10;
    private static final int PESO_FC_VALIDA = 10;
    private static final int PESO_RITMO_LIMIAR = 15;
    private static final int PESO_RPE = 10;
    private static final int PESO_CONSISTENCIA = 10;
    private static final int PESO_PROVA_RECENTE = 10;
    private static final int PESO_FONTE_CONFIAVEL = 15;

    private static final int TIER_A_MINIMO = 75;
    private static final int TIER_B_MINIMO = 45;

    @Override
    public ConfidenceScoreResult calcular(ConfidenceScorerInput input) {
        if (input == null) {
            throw new IllegalArgumentException("input nao pode ser nulo");
        }

        List<NormalizedActivity> historico = input.historicoDeduplicado() != null
                ? input.historicoDeduplicado() : List.of();

        double pontosHistorico = pontuarHistorico(historico);
        double pontosOnboarding = input.onboardingCompleto() ? PESO_ONBOARDING_COMPLETO : 0;
        double pontosFc = pontuarFcValida(input, historico) ? PESO_FC_VALIDA : 0;
        double pontosRitmo = input.paceLimiar() != null ? PESO_RITMO_LIMIAR : 0;
        double pontosRpe = pontuarRpe(historico);
        double pontosConsistencia = pontuarConsistenciaSemanal(historico);
        double pontosProva = input.temProvaRecente() ? PESO_PROVA_RECENTE : 0;
        double pontosFonte = pontuarFonteConfiavel(historico);

        int scoreBruto = (int) Math.round(pontosHistorico + pontosOnboarding + pontosFc + pontosRitmo
                + pontosRpe + pontosConsistencia + pontosProva + pontosFonte);
        scoreBruto = Math.max(0, Math.min(100, scoreBruto));

        ConfidenceTier tierBase = classificar(scoreBruto);
        ConfidenceTier tierFinal = tierBase;
        boolean bonusAplicado = false;
        if (input.preenchidoPorCoach() && tierBase != ConfidenceTier.A) {
            tierFinal = subirUmTier(tierBase);
            bonusAplicado = true;
        }

        log.info("Confidence score calculado: bruto={}, tierBase={}, tierFinal={}, bonusCoach={}",
                scoreBruto, tierBase, tierFinal, bonusAplicado);

        return new ConfidenceScoreResult(scoreBruto, tierFinal, bonusAplicado);
    }

    private double pontuarHistorico(List<NormalizedActivity> historico) {
        int semanas = HistoricoUtils.semanasObservadas(historico);
        double proporcao = Math.min(1.0, semanas / (double) SEMANAS_TETO_HISTORICO);
        return PESO_HISTORICO * proporcao;
    }

    private boolean pontuarFcValida(ConfidenceScorerInput input, List<NormalizedActivity> historico) {
        if (input.fcMaxima() != null || input.fcRepouso() != null) {
            return true;
        }
        if (historico.isEmpty()) {
            return false;
        }
        long comFc = historico.stream().filter(a -> a.averageHeartRate() != null).count();
        return (comFc / (double) historico.size()) >= LIMIAR_FC_PROPORCAO_ATIVIDADES;
    }

    private double pontuarRpe(List<NormalizedActivity> historico) {
        if (historico.isEmpty()) {
            return 0;
        }
        long comRpe = historico.stream().filter(a -> a.rpe() != null).count();
        return PESO_RPE * (comRpe / (double) historico.size());
    }

    private double pontuarConsistenciaSemanal(List<NormalizedActivity> historico) {
        if (historico.isEmpty()) {
            return 0;
        }
        int semanasObservadas = Math.max(1, HistoricoUtils.semanasObservadas(historico));
        long semanasComAtividade = historico.stream()
                .map(a -> semanaDe(a.date()))
                .distinct()
                .count();
        double proporcao = Math.min(1.0, semanasComAtividade / (double) semanasObservadas);
        return PESO_CONSISTENCIA * proporcao;
    }

    private long semanaDe(LocalDate data) {
        return data.toEpochDay() / 7;
    }

    private double pontuarFonteConfiavel(List<NormalizedActivity> historico) {
        if (historico.isEmpty()) {
            return 0;
        }
        long confiaveis = historico.stream()
                .filter(a -> FontePriority.de(a.source()) <= PRIORIDADE_FONTE_CONFIAVEL_MAXIMA)
                .count();
        return PESO_FONTE_CONFIAVEL * (confiaveis / (double) historico.size());
    }

    private ConfidenceTier classificar(int score) {
        if (score >= TIER_A_MINIMO) {
            return ConfidenceTier.A;
        }
        if (score >= TIER_B_MINIMO) {
            return ConfidenceTier.B;
        }
        return ConfidenceTier.C;
    }

    private ConfidenceTier subirUmTier(ConfidenceTier tier) {
        return switch (tier) {
            case C -> ConfidenceTier.B;
            case B -> ConfidenceTier.A;
            case A -> ConfidenceTier.A;
        };
    }
}
