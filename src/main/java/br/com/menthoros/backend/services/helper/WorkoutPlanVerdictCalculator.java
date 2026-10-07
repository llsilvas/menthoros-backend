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
 *
 * <p><b>Identificadores em PT-BR (desvio intencional da ADR-0007):</b> {@code veredito} convive
 * com {@code comoFoi}/{@code reconhecimento}/{@code esforco}, todos PT-BR legado, no mesmo
 * {@code AthleteWorkoutAnalysisOutputDto} — decisão registrada em design.md D0 da
 * add-athlete-workout-verdict-chip.</p>
 */
@Component
public class WorkoutPlanVerdictCalculator {

    private final BigDecimal limiteInferiorFator;
    private final BigDecimal limiteSuperiorFator;
    private final int deltaRpe;

    /**
     * Construtor de teste — valores explícitos, sem default implícito. A única fonte de verdade
     * para o default de produção é {@link WorkoutAnalysisProperties.Verdict} (achado do
     * code-reviewer: dois defaults hardcoded em lugares diferentes divergem silenciosamente se
     * alguém recalibrar um e esquecer o outro).
     */
    public WorkoutPlanVerdictCalculator(double toleranciaPct, int deltaRpe) {
        BigDecimal fracao = BigDecimal.valueOf(toleranciaPct).divide(BigDecimal.valueOf(100));
        this.limiteInferiorFator = BigDecimal.ONE.subtract(fracao);
        this.limiteSuperiorFator = BigDecimal.ONE.add(fracao);
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

        Desvio duracao = classificar(toBigDecimal(executado.duracaoMin()), toBigDecimal(planejado.duracaoMin()));
        Desvio distancia = classificar(executado.distanciaKm(), planejado.distanciaKm());

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

    /**
     * Compara por multiplicação (planejado × fator), nunca por divisão (executado / planejado) —
     * achado do Codex adversarial review: {@code 6.9 / 6.0} em {@code double} produz
     * {@code 1.1500000000000001}, cruzando o limite de 115% inclusivo por erro de arredondamento
     * binário. {@link BigDecimal} com aritmética exata elimina a classe inteira do problema.
     */
    private Desvio classificar(BigDecimal executado, BigDecimal planejado) {
        if (planejado == null || planejado.compareTo(BigDecimal.ZERO) <= 0) return Desvio.NAO_APLICAVEL;
        if (executado == null) return Desvio.NAO_COBERTA;

        BigDecimal limiteInferior = planejado.multiply(limiteInferiorFator);
        BigDecimal limiteSuperior = planejado.multiply(limiteSuperiorFator);

        if (executado.compareTo(limiteInferior) < 0) return Desvio.ABAIXO;
        if (executado.compareTo(limiteSuperior) > 0) return Desvio.ACIMA;
        return Desvio.DENTRO;
    }

    private static BigDecimal toBigDecimal(Long valor) {
        return valor == null ? null : BigDecimal.valueOf(valor);
    }

    private enum Desvio { ABAIXO, DENTRO, ACIMA, NAO_COBERTA, NAO_APLICAVEL }
}
