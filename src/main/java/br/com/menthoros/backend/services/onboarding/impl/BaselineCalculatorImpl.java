package br.com.menthoros.backend.services.onboarding.impl;

import br.com.menthoros.backend.entity.MetricasDiarias;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.repository.MetricasDiariasRepository;
import br.com.menthoros.backend.services.TsbService;
import br.com.menthoros.backend.services.onboarding.BaselineCalculator;
import br.com.menthoros.backend.services.onboarding.BaselineResult;
import br.com.menthoros.backend.services.onboarding.NormalizedActivity;
import br.com.menthoros.backend.services.onboarding.OrigemDado;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Implementacao do Baseline Calculator (design.md Decisao 3/6,
 * athlete-onboarding-baseline). Ver javadoc de {@link BaselineCalculator}
 * para a formula continua de blend real+heuristica.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BaselineCalculatorImpl implements BaselineCalculator {

    private static final int SEMANAS_PARA_BASELINE_DIRETO = 8; // Cenario A — mesmo limiar do Confidence Scorer

    /**
     * Heuristica de CTL por nivel de experiencia (Cenario C — sem historico
     * real). Hipotese v1, mesma classe de threshold hardcoded documentado de
     * {@code planner-rules.yml} (deterministic-planner-engine) — calibravel
     * com dado real, nao bloqueante para implementar. ATL heuristico assume
     * o mesmo valor de CTL (atleta "em forma neutra", TSB=0) — nao ha sinal
     * nenhum de fadiga recente para um atleta sem historico algum.
     */
    private static final Map<NivelExperiencia, Double> CTL_HEURISTICO = new EnumMap<>(NivelExperiencia.class);

    static {
        CTL_HEURISTICO.put(NivelExperiencia.INICIANTE, 25.0);
        CTL_HEURISTICO.put(NivelExperiencia.INTERMEDIARIO, 40.0);
        CTL_HEURISTICO.put(NivelExperiencia.AVANCADO, 55.0);
        CTL_HEURISTICO.put(NivelExperiencia.ELITE, 70.0);
    }

    private final TsbService tsbService;
    private final MetricasDiariasRepository metricasDiariasRepository;

    @Override
    public BaselineResult calcular(UUID atletaId, NivelExperiencia nivelExperiencia, List<NormalizedActivity> historicoDeduplicado) {
        if (atletaId == null) {
            throw new IllegalArgumentException("atletaId nao pode ser nulo");
        }
        if (nivelExperiencia == null) {
            throw new IllegalArgumentException("nivelExperiencia nao pode ser nulo");
        }

        tsbService.recalcularHistoricoCompleto(atletaId);
        MetricasDiarias metricas = metricasDiariasRepository.findLatestByAtletaId(atletaId).orElse(null);
        double ctlReal = metricas != null ? metricas.getCtl() : 0.0;
        double atlReal = metricas != null ? metricas.getAtl() : 0.0;

        int semanasObservadas = calcularSemanasObservadas(historicoDeduplicado);
        double proporcaoHeuristica = calcularProporcaoHeuristica(semanasObservadas);

        double ctlHeuristico = CTL_HEURISTICO.getOrDefault(nivelExperiencia, CTL_HEURISTICO.get(NivelExperiencia.INICIANTE));
        double atlHeuristico = ctlHeuristico; // forma neutra — sem sinal de fadiga recente

        double ctlFinal = blend(ctlReal, ctlHeuristico, proporcaoHeuristica);
        double atlFinal = blend(atlReal, atlHeuristico, proporcaoHeuristica);
        double tsbFinal = ctlFinal - atlFinal;

        OrigemDado origem = proporcaoHeuristica > 0 ? OrigemDado.ESTIMATED : OrigemDado.MEASURED;

        log.info("Baseline calculado para atleta {}: semanasObservadas={}, proporcaoHeuristica={}, ctl={}, atl={}, tsb={}, origem={}",
                atletaId, semanasObservadas, proporcaoHeuristica, ctlFinal, atlFinal, tsbFinal, origem);

        return new BaselineResult(ctlFinal, origem, atlFinal, origem, tsbFinal, origem);
    }

    private int calcularSemanasObservadas(List<NormalizedActivity> historico) {
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

    /** 0 (>= 8 semanas, Cenario A) a 1 (0 semanas, Cenario C) — interpolacao linear (mesmo padrao do Confidence Scorer). */
    private double calcularProporcaoHeuristica(int semanasObservadas) {
        if (semanasObservadas >= SEMANAS_PARA_BASELINE_DIRETO) {
            return 0.0;
        }
        return (SEMANAS_PARA_BASELINE_DIRETO - semanasObservadas) / (double) SEMANAS_PARA_BASELINE_DIRETO;
    }

    private double blend(double valorReal, double valorHeuristico, double proporcaoHeuristica) {
        return valorReal * (1 - proporcaoHeuristica) + valorHeuristico * proporcaoHeuristica;
    }
}
