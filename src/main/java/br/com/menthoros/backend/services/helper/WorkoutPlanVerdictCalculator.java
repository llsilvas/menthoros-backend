package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.config.core.WorkoutAnalysisProperties;
import br.com.menthoros.backend.dto.output.AthleteWorkoutAnalysisOutputDto.Executado;
import br.com.menthoros.backend.dto.output.AthleteWorkoutAnalysisOutputDto.Planejado;
import br.com.menthoros.backend.enums.WorkoutPlanVerdict;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Calcula o {@link WorkoutPlanVerdict} de um treino, comparando executado vs. planejado sem LLM
 * (add-athlete-workout-verdict-chip, D2). Regra, em ordem — a primeira que casar vence:
 *
 * <ol>
 *   <li>sem planejado vinculado → {@code null};</li>
 *   <li>RPE informado ≥ esperado + {@code deltaRpe} → {@code ESFORCO_ACIMA_DO_ESPERADO};</li>
 *   <li>desvio misto — uma dimensão (duração/distância) acima da tolerância e outra abaixo →
 *       {@code ACIMA_DO_PLANO} (achado do Codex adversarial review: a ordem "abaixo antes de
 *       acima" mascarava o excesso de uma dimensão atrás do déficit da outra);</li>
 *   <li>alguma dimensão comparável abaixo da tolerância → {@code ABAIXO_DO_PLANO};</li>
 *   <li>alguma dimensão comparável acima da tolerância → {@code ACIMA_DO_PLANO};</li>
 *   <li>todas as dimensões comparáveis estão dentro da tolerância e nenhuma dimensão planejada
 *       ficou sem contrapartida executada → {@code DENTRO_DO_PLANO};</li>
 *   <li>caso contrário — há dimensão planejada sem executado correspondente e nenhum desvio
 *       apareceu nos passos acima — → {@code null}: dado insuficiente para afirmar "dentro do
 *       plano" (achado do Codex: dado incompleto não pode virar aprovação silenciosa).</li>
 * </ol>
 *
 * <p>Idempotent: YES — mesma entrada sempre produz a mesma saída. Side Effects: NONE.
 * Tenant-aware: NO — pura transformação de DTOs, sem acesso a dados persistidos.</p>
 */
@Component
public class WorkoutPlanVerdictCalculator {

    private static final double DEFAULT_TOLERANCIA_PCT = 15.0;
    private static final int DEFAULT_DELTA_RPE = 2;

    private final double toleranciaPct;
    private final int deltaRpe;

    public WorkoutPlanVerdictCalculator() {
        this(DEFAULT_TOLERANCIA_PCT, DEFAULT_DELTA_RPE);
    }

    public WorkoutPlanVerdictCalculator(double toleranciaPct, int deltaRpe) {
        this.toleranciaPct = toleranciaPct;
        this.deltaRpe = deltaRpe;
    }

    @Autowired
    public WorkoutPlanVerdictCalculator(WorkoutAnalysisProperties properties) {
        this(properties.getVerdict().getToleranciaPct(), properties.getVerdict().getDeltaRpe());
    }

    public WorkoutPlanVerdict calcular(Executado executado, Planejado planejado) {
        Objects.requireNonNull(executado, "executado não pode ser nulo");
        if (planejado == null) return null;

        boolean rpeComparavel = executado.rpe() != null && planejado.rpeEsperado() != null;
        if (rpeComparavel && executado.rpe() >= planejado.rpeEsperado() + deltaRpe) {
            return WorkoutPlanVerdict.ESFORCO_ACIMA_DO_ESPERADO;
        }

        Desvio duracao = classificar(toDouble(executado.duracaoMin()), toDouble(planejado.duracaoMin()));
        Desvio distancia = classificar(toDouble(executado.distanciaKm()), toDouble(planejado.distanciaKm()));

        boolean algumaAbaixo = duracao == Desvio.ABAIXO || distancia == Desvio.ABAIXO;
        boolean algumaAcima = duracao == Desvio.ACIMA || distancia == Desvio.ACIMA;
        boolean algumaNaoCoberta = duracao == Desvio.NAO_COBERTA || distancia == Desvio.NAO_COBERTA;
        boolean algumaComparavel = duracao != Desvio.NAO_APLICAVEL || distancia != Desvio.NAO_APLICAVEL || rpeComparavel;

        if (algumaAbaixo && algumaAcima) return WorkoutPlanVerdict.ACIMA_DO_PLANO;
        if (algumaAbaixo) return WorkoutPlanVerdict.ABAIXO_DO_PLANO;
        if (algumaAcima) return WorkoutPlanVerdict.ACIMA_DO_PLANO;
        if (algumaNaoCoberta) return null;
        if (algumaComparavel) return WorkoutPlanVerdict.DENTRO_DO_PLANO;
        return null;
    }

    private Desvio classificar(Double executado, Double planejado) {
        if (planejado == null || planejado <= 0) return Desvio.NAO_APLICAVEL;
        if (executado == null) return Desvio.NAO_COBERTA;

        double ratio = executado / planejado;
        double inferior = 1 - toleranciaPct / 100.0;
        double superior = 1 + toleranciaPct / 100.0;

        if (ratio < inferior) return Desvio.ABAIXO;
        if (ratio > superior) return Desvio.ACIMA;
        return Desvio.DENTRO;
    }

    private static Double toDouble(Long valor) {
        return valor == null ? null : valor.doubleValue();
    }

    private static Double toDouble(BigDecimal valor) {
        return valor == null ? null : valor.doubleValue();
    }

    private enum Desvio { ABAIXO, DENTRO, ACIMA, NAO_COBERTA, NAO_APLICAVEL }
}
