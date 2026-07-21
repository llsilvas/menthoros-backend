package br.com.menthoros.backend.services.onboarding.impl;

import br.com.menthoros.backend.domain.planner.InjuryRiskLevel;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.events.AtletaPresoEmCalibracaoEvent;
import br.com.menthoros.backend.services.MetricasAdesaoService;
import br.com.menthoros.backend.services.onboarding.BaselineCalculator;
import br.com.menthoros.backend.services.onboarding.BaselineResult;
import br.com.menthoros.backend.services.onboarding.CalibrationEvaluation;
import br.com.menthoros.backend.services.onboarding.CalibrationService;
import br.com.menthoros.backend.services.onboarding.CalibrationStage;
import br.com.menthoros.backend.services.onboarding.ConfidenceScoreResult;
import br.com.menthoros.backend.services.onboarding.ConfidenceScorer;
import br.com.menthoros.backend.services.onboarding.ConfidenceScorerInput;
import br.com.menthoros.backend.services.onboarding.NormalizedActivity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Implementacao do Calibration Service (design.md Decisao 5,
 * athlete-onboarding-baseline).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CalibrationServiceImpl implements CalibrationService {

    private static final int SEMANA_OBSERVATION = 1;
    private static final int SEMANA_CALIBRATION = 2;
    private static final int SEMANA_LIMITE_ESPERADO = 4; // alem disso, atleta esta "preso"

    private static final int SCORE_MINIMO_SAIDA = 45;
    private static final double ADERENCIA_MINIMA_SAIDA = 70.0;

    private final BaselineCalculator baselineCalculator;
    private final ConfidenceScorer confidenceScorer;
    private final MetricasAdesaoService metricasAdesaoService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public CalibrationStage determinarEstagio(int semanaDesdeInicioCalibracao) {
        if (semanaDesdeInicioCalibracao <= SEMANA_OBSERVATION) {
            return CalibrationStage.OBSERVATION;
        }
        if (semanaDesdeInicioCalibracao == SEMANA_CALIBRATION) {
            return CalibrationStage.CALIBRATION;
        }
        return CalibrationStage.STABILIZATION; // semanas 3-4, e alem (preso)
    }

    @Override
    public CalibrationEvaluation avaliarSemana(
            UUID atletaId,
            UUID tenantId,
            int semanaDesdeInicioCalibracao,
            LocalDate dataReferenciaSemana,
            NivelExperiencia nivelExperiencia,
            List<NormalizedActivity> historicoDeduplicado,
            ConfidenceScorerInput confidenceInput,
            InjuryRiskLevel injuryRiskLevel) {

        CalibrationStage stage = determinarEstagio(semanaDesdeInicioCalibracao);

        // Re-baseline e re-score semanal — sempre recalculado, nunca reaproveita valor anterior
        // (score bidirecional: pode subir OU descer a cada semana, design.md Decisao 5).
        BaselineResult baseline = baselineCalculator.calcular(atletaId, nivelExperiencia, historicoDeduplicado);
        ConfidenceScoreResult confidenceScore = confidenceScorer.calcular(confidenceInput);

        double percentualRealizacao = metricasAdesaoService
                .getAdesaoSemana(atletaId.toString(), dataReferenciaSemana)
                .percentualRealizacao();

        boolean elegivel = confidenceScore.scoreBruto() >= SCORE_MINIMO_SAIDA
                && injuryRiskLevel != InjuryRiskLevel.HIGH_RISK
                && percentualRealizacao >= ADERENCIA_MINIMA_SAIDA;

        if (!elegivel && semanaDesdeInicioCalibracao > SEMANA_LIMITE_ESPERADO) {
            eventPublisher.publishEvent(new AtletaPresoEmCalibracaoEvent(atletaId, tenantId, semanaDesdeInicioCalibracao));
            log.warn("Atleta {} preso em CALIBRATION ha {} semanas (esperado ate {})",
                    atletaId, semanaDesdeInicioCalibracao, SEMANA_LIMITE_ESPERADO);
        }

        log.info("Calibracao semana {} para atleta {}: stage={}, score={}, percentualRealizacao={}, elegivel={}",
                semanaDesdeInicioCalibracao, atletaId, stage, confidenceScore.scoreBruto(), percentualRealizacao, elegivel);

        return new CalibrationEvaluation(stage, baseline, confidenceScore, elegivel);
    }
}
